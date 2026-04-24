package com.dropie.domain.payment.service;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderStatus;
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

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
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

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.confirmPayment("test@email.com", 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        // 금액 검증에서 막혔으므로 토스 API 호출이 없어야 함
        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("결제 확인 실패 — 토스 API 실패 시 주문 CANCELED, 재고 복원")
    void 결제_확인_토스실패_주문취소() {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest("payKey123", 5500);

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(tossPaymentClient.confirm(any(), any(), any(Integer.class)))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));

        // when & then
        assertThatThrownBy(() -> paymentService.confirmPayment("test@email.com", 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        // 토스 실패 후 롤백 — 주문이 CANCELED 상태여야 함
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
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

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
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
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

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
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

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

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() ->
                paymentService.confirmPayment("test@email.com", 1L, new PaymentConfirmRequest("key", 5500)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_PENDING);
    }

}