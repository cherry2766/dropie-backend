package com.dropie.domain.order.service;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.user.entity.User;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

// AOP 없는 순수 로직이므로 Mockito만으로 테스트 가능
@ExtendWith(MockitoExtension.class)
// @BeforeEach의 stub이 일부 테스트에서 호출되지 않아도 UnnecessaryStubbingException 방지
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLockOrderFacadeTest {

    @Mock
    private OrderService orderService;

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private RedisLockOrderFacade redisLockOrderFacade;

    @Mock
    private RLock rLock;

    private CustomUserDetails userDetails;
    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(1L);
        userDetails = new CustomUserDetails(mockUser);

        request = CreateOrderRequest.builder()
                .receiverName("체리콩")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address1("서울시 강남구")
                .address2("101호")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // 상품 ID별 개별 락과 MultiLock 모두 같은 rLock Mock 반환
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(redissonClient.getMultiLock(any())).willReturn(rLock);
    }

    @Test
    @DisplayName("락 획득 성공 → 주문 성공 → 락 해제")
    void 락_획득_성공_주문_성공_락_해제() throws InterruptedException {
        // given
        OrderCreateResponse mockResponse = mock(OrderCreateResponse.class);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(orderService.createOrder(any(), any())).willReturn(mockResponse);

        // when
        OrderCreateResponse result = redisLockOrderFacade.createOrder(request, userDetails);

        // then
        assertThat(result).isNotNull();
        then(orderService).should(times(1)).createOrder(any(), any());
        then(rLock).should(times(1)).unlock(); // finally에서 반드시 락 해제
    }

    @Test
    @DisplayName("락 획득 실패(waitTime 초과) → LOCK_ACQUISITION_FAILED, 주문 로직 미진입")
    void 락_획득_실패_LOCK_ACQUISITION_FAILED() throws InterruptedException {
        // given — tryLock이 false 반환 (대기 시간 초과)
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> redisLockOrderFacade.createOrder(request, userDetails))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);

        // 락 미획득 시 주문 로직 진입하면 안됨
        then(orderService).should(never()).createOrder(any(), any());
    }

    @Test
    @DisplayName("인터럽트 발생 → LOCK_ACQUISITION_FAILED, 인터럽트 상태 복구")
    void 인터럽트_발생_LOCK_ACQUISITION_FAILED() throws InterruptedException {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .willThrow(InterruptedException.class);

        // then & then
        assertThatThrownBy(() -> redisLockOrderFacade.createOrder(request, userDetails))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);

        // Thread.currentThread().interrupt()가 호출됐는지 확인
        // (JVM 규칙: InterruptedException catch 후 인터럽트 상태 복구 의무)
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // 테스트 격리를 위해 인터럽트 상태 초기화
    }

    @Test
    @DisplayName("leaseTime 초과로 락 자동 해제 → unlock() 호출 안 함")
    void leaseTime_초과_이미_해제된_락_unlock_미호출() throws InterruptedException {
        // given — leaseTime 초과로 Redis가 이미 락을 자동 해제한 상황
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(false); // 이미 해제됨
        given(orderService.createOrder(any(), any())).willReturn(mock(OrderCreateResponse.class));

        // when
        redisLockOrderFacade.createOrder(request, userDetails);

        // then — isHeldByCurrentThread()=false이므로 unlock() 호출 안 함
        // 이미 해제된 락에 unlock() 호출하면 IllegalMonitorStateException 발생
        then(rLock).should(never()).unlock();
    }

    @Test
    @DisplayName("주문 처리 중 예외 발생 → finally에서 락 반드시 해제")
    void 주문_처리_중_예외_발생시_finally_락_해제() throws InterruptedException {
        // given — 락 획득 성공, 주문 처리 중 예외 발생
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(orderService.createOrder(any(), any()))
                .willThrow(new RuntimeException("DB 처리 중 예외"));

        // when & then
        assertThatThrownBy(() -> redisLockOrderFacade.createOrder(request, userDetails))
                .isInstanceOf(RuntimeException.class);

        // 주문이 실패해도 finally 블록에서 반드시 락 해제
        then(rLock).should(times(1)).unlock();
    }
}