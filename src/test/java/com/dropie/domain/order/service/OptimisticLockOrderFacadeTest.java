package com.dropie.domain.order.service;

import com.dropie.domain.order.dto.request.CreateOrderRequest;
import com.dropie.domain.order.dto.response.OrderCreateResponse;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.user.entity.User;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

// @Retryable은 Spring AOP 기반이라 Mockito만으로는 재시도 동작 테스트 불가
// → Spring이 프록시 객체를 만들어야 @Retryable이 동작하므로 @SpringBootTest 사용
@SpringBootTest
@TestPropertySource(properties = "app.lock.type=optimistic")
class OptimisticLockOrderFacadeTest {

    @Autowired
    private OptimisticLockOrderFacade optimisticLockOrderFacade;

    @MockitoBean
    private OrderService orderService;

    // RedisConfig.redissonClient()가 Redis에 연결 시도하는 걸 방지
    @MockitoBean
    private RedissonClient redissonClient;

    // 테스트 환경에서 외부 인프라 없이 컨텍스트 로드 가능하게 Mock 처리
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private JavaMailSender javaMailSender;

    private CustomUserDetails userDetails;
    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        // 단위 테스트용 Mock User — 실제 DB 저장 불필요, id/email만 있으면 충분
        User mockUser = mock(User.class);
        given(mockUser.getId()).willReturn(1L);
        userDetails = new CustomUserDetails(mockUser);

        request = CreateOrderRequest.builder()
                .receiverName("강체리")
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
    }

    @Test
    @DisplayName("첫 번째 시도에서 주문 성공 — 재시도 없이 1회만 호출")
    void 첫_번째_시도_주문_성공() {
        // given
        OrderCreateResponse mockResponse = mock(OrderCreateResponse.class);
        given(orderService.createOrder(any(), any())).willReturn(mockResponse);

        // when
        OrderCreateResponse result = optimisticLockOrderFacade.createOrder(request, userDetails);

        // then
        assertThat(result).isNotNull();
        then(orderService).should(times(1)).createOrder(any(), any());
    }

    @Test
    @DisplayName("1회 충돌 후 재시도 성공 — 총 2번 호출")
    void 충돌_1회_후_재시도_성공() {
        // given - 첫 번째는 낙관적 락 충돌, 두 번째는 성공
        OrderCreateResponse mockResponse = mock(OrderCreateResponse.class);
        given(orderService.createOrder(any(), any()))
                .willThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L))
                .willReturn(mockResponse);

        // when
        OrderCreateResponse result = optimisticLockOrderFacade.createOrder(request, userDetails);

        // then — 1회 실패 후 재시도 성공, 총 2번 호출 확인
        assertThat(result).isNotNull();
        then(orderService).should(times(2)).createOrder(any(), any());
    }

    @Test
    @DisplayName("재시도 3회 모두 소진 후 ORDER_CONFLICT 예외")
    void 재시도_3회_소진_후_ORDER_CONFLICT_예외() {
        // given — 항상 낙관적 락 충돌 (3번 모두 실패)
        given(orderService.createOrder(any(), any()))
                .willThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        // when & then — @Recover 메서드가 실행되어 ORDER_CONFLICT 예외로 전환
        assertThatThrownBy(() -> optimisticLockOrderFacade.createOrder(request, userDetails))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_CONFLICT);

        // maxAttempts=3이므로 정확히 3번 시도
        then(orderService).should(times(3)).createOrder(any(), any());
    }
}