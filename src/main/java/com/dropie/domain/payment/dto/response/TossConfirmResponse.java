package com.dropie.domain.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 토스 결제 승인 API 응답을 받는 DTO
// 토스 공식 문서: https://docs.tosspayments.com/reference#결제-승인
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TossConfirmResponse {
    private String paymentKey;
    private String orderId;       // 우리 orderNumber
    private int totalAmount;
    private String method;        // "카드", "가상계좌" 등
    private String approvedAt;    // "2026-04-21T12:00:00+09:00" (ISO 8601 + 시간대)
    private String status;        // "DONE" = 승인 완료
}
