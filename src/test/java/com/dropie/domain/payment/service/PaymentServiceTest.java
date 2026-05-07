package com.dropie.domain.payment.service;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.event.OrderRollbackEvent;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.payment.client.TossPaymentClient;
import com.dropie.domain.payment.dto.request.PaymentConfirmRequest;
import com.dropie.domain.payment.dto.response.PaymentConfirmResponse;
import com.dropie.domain.payment.dto.response.TossConfirmResponse;
import com.dropie.domain.payment.entity.Payment;
import com.dropie.domain.payment.repository.PaymentRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TossPaymentClient tossPaymentClient;

    // Redis 키 선삭제(redisTemplate.delete)에 필요. delete()는 별도 stub 없이도
    // 기본 mock 반환값(0L)으로 안전하게 동작하므로 필드 선언만 추가하면 됨
    @Mock
    private StringRedisTemplate redisTemplate;

    // OrderPaidEvent 발행(성공 케이스) + OrderRollbackEvent 발행(실패 케이스)에 필요
    // → mock으로 두면 publishEvent 호출이 no-op으로 처리되어 NPE 방지
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@email.com")
                .nickname("체리")
                .role(Role.USER)
                .build();

        // PENDING 상태의 주문 (결제 전 상태)
        order = Order.builder()
                .user(user)
                .orderNumber("ORD-20260421-000001")
                .totalPrice(5500)
                .build();
        // 기본 status = PENDING
    }

    @Test
    @DisplayName("결제 확인 성공 — 주문 PAID로 변경되고 Payment가 저장됨")
    void 결제_확인_성공() {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest("payKey123", 5500);

        TossConfirmResponse tossResponse = TossConfirmResponse.builder()
                .paymentKey("payKey123")
                .totalAmount(5500)
                .method("CARD")
                .approvedAt("2026-04-23T10:15:30+09:00")
                .build();

        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(tossPaymentClient.confirm(any(), any(), any(Integer.class))).willReturn(tossResponse);
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        PaymentConfirmResponse response = paymentService.confirmPayment("test@email.com", 1L, request);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getStatus()).isEqualTo("PAID");
        then(paymentRepository).should().save(any(Payment.class));
        then(tossPaymentClient).should().confirm(any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("결제 확인 실패 — 금액 불일치 시 PAYMENT_AMOUNT_MISMATCH 예외")
    void 결제_확인_금액불일치_예외() {
        // given: 주문 금액은 5500인데 요청 금액은 9999
        PaymentConfirmRequest request = new PaymentConfirmRequest("payKey123", 9999);

        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.confirmPayment("test@email.com", 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        // 금액 검증에서 막혔으므로 토스 API 호출이 없어야 함
        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("결제 확인 실패 — 토스 API 실패 시 OrderRollbackEvent 발행 + 예외 전파")
    void 결제_확인_토스실패_보상_이벤트_발행() {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest("payKey123", 5500);

        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(tossPaymentClient.confirm(any(), any(), any(Integer.class)))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));

        // when & then: 예외가 호출자에게 전파되어야 함
        assertThatThrownBy(() -> paymentService.confirmPayment("test@email.com", 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        // 보상 이벤트 발행 검증 — 실제 cancel/재고 복원은 OrderRollbackEventListener가 담당
        // (단위 테스트 환경엔 리스너가 동작하지 않으므로 in-memory order 상태는 PENDING 그대로)
        // 실제 보상 흐름은 PaymentRollbackTest 통합 테스트에서 검증
        then(eventPublisher).should().publishEvent(any(OrderRollbackEvent.class));
    }

    @Test
    @DisplayName("결제 확인 멱등성 — 이미 PAID된 주문이면 토스 재호출 없이 기존 결제 반환")
    void 결제_확인_이미처리된결제_반환() {
        // given : 주문이 이미 PAID 상태
        order.confirm();    // PENDING → PAID

        Payment existingPayment = Payment.builder()
                .order(order)
                .paymentKey("payKey123")
                .amount(5500)
                .method("카드")
                .approvedAt(LocalDateTime.now())
                .build();

        PaymentConfirmRequest request = new PaymentConfirmRequest("payKey123", 5500);

        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrder(order)).willReturn(Optional.of(existingPayment));

        // when
        PaymentConfirmResponse response = paymentService.confirmPayment("test@email.com", 1L, request);

        // then — 토스 API 재호출 없음 (중복 결제 방지)
        then(tossPaymentClient).shouldHaveNoInteractions();
        assertThat(response.getStatus()).isEqualTo("PAID");
        assertThat(response.getPaymentKey()).isEqualTo("payKey123");
    }

    @Test
    @DisplayName("결제 확인 실패 — 본인 주문이 아니면 ORDER_ACCESS_DENIED 예외")
    void 결제_확인_타인주문_예외() {
        // given: order는 test@email.com 소유인데 other@email.com이 결제 시도
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() ->
                paymentService.confirmPayment("other@email.com", 1L, new PaymentConfirmRequest("key", 5500)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("결제 확인 실패 — 존재하지 않는 주문이면 OrderNotFoundException 예외")
    void 결제_확인_없는주문_예외() {
        // given
        given(orderRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                paymentService.confirmPayment("test@email.com", 999L, new PaymentConfirmRequest("key", 5500)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("결제 확인 실패 — CANCELED 상태 주문이면 ORDER_NOT_PENDING 예외")
    void 결제_확인_취소된주문_예외() {
        // given: 이미 취소된 주문
        order.cancel();  // PENDING → CANCELED

        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() ->
                paymentService.confirmPayment("test@email.com", 1L, new PaymentConfirmRequest("key", 5500)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_PENDING);
    }

}