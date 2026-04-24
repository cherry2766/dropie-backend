package com.dropie.domain.payment.client;

import com.dropie.domain.payment.dto.response.TossConfirmResponse;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

// 토스 승인 API 호출
@Component
@Slf4j
public class TossPaymentClient {

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    private static final String TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    /**
     * 토스페이먼츠 결제 승인 API를 호출한다.
     * <p>
     * 인증 방식: HTTP Basic Auth
     * - 토스는 secretKey 뒤에 ':'를 붙이고 Base64로 인코딩한 값을 Authorization 헤더에 넣음
     * - 예) Base64("test_sk_xxx:") → "Authorization: Basic dGVzdF9za19..."
     *
     * @param paymentKey 토스가 발급한 결제 키
     * @param orderId    우리 시스템의 주문번호 (orderNumber)
     * @param amount     결제 금액 (주문 금액과 일치해야 함)
     */
    public TossConfirmResponse confirm(String paymentKey, String orderId, int amount) {
        // secretKey 뒤에 ':' 붙이고 Base64 인코딩 — 토스 인증 규격
        String encodedKey = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            return RestClient.create()
                    .post()
                    .uri(TOSS_CONFIRM_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "paymentKey", paymentKey,
                            "orderId", orderId,
                            "amount", amount
                    ))
                    .retrieve()
                    .body(TossConfirmResponse.class);
        } catch (Exception e) {
            // 네트워크 오류, 토스 측 오류(잔액 부족, 한도 초과 등) 모두 포함
            log.error("[TossPaymentClient] 결제 승인 실패 — paymentKey: {}, error: {}",
                    paymentKey, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
    }
}
