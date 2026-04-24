package com.dropie.domain.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 토스 → 백엔드로 자동 전송되는 이벤트 DTO
@Getter
@NoArgsConstructor
public class TossWebhookRequest {
    private String eventType;  // "PAYMENT_STATUS_CHANGED" 등
    private String paymentKey;
    private String orderId;
    private String status;     // "DONE", "CANCELED" 등
}
