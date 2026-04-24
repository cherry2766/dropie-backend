package com.dropie.domain.payment.dto.response;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.payment.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 결제 완료 후 프론트에 내려주는 응답 DTO
@Getter
@Builder
public class PaymentConfirmResponse {

    private Long orderId;
    private String orderNumber;
    private String status;  // "PAID"
    private String paymentKey;
    private int amount;
    private String method;
    private LocalDateTime approvedAt;

    public static PaymentConfirmResponse of(Order order, Payment payment) {
        return PaymentConfirmResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .approvedAt(payment.getApprovedAt())
                .build();
    }
}
