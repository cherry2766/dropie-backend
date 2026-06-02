package com.dropie.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 프론트가 토스 결제창 완료 후 백엔드로 보내는 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    // 토스가 발급한 결제 고유키 - 토스 승인 API 호출 시 필수
    @NotBlank(message = "paymentKey는 필수입니다.")
    private String paymentKey;

    // 결제 금액 - 주문 금액과 일치하는지 백엔드에서 검증 (변조 방지)
    @Positive(message = "결제 금액은 양수여야 합니다.")
    private int amount;
}
