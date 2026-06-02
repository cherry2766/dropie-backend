package com.dropie.domain.payment.controller;

import com.dropie.domain.payment.dto.request.TossWebhookRequest;
import com.dropie.domain.payment.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제 웹훅", description = "토스페이먼츠 웹훅 수신 — 프론트 콜백 누락 시 안전망")
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentRepository paymentRepository;

    /**
     * 토스페이먼츠 웹훅 수신 엔드포인트
     * <p>
     * 웹훅이란? 토스가 결제 상태 변경(성공/실패/취소)을 자동으로 우리 서버에 알려주는 기능.
     * 프론트가 결제 완료 콜백을 놓쳤을 때 안전망 역할을 함 (이중 안전망).
     * <p>
     * 설정 방법: 토스 개발자 대시보드 → 웹훅 → 이 URL 등록
     * (테스트 환경에서는 ngrok으로 로컬 서버를 외부에 노출시켜 테스트 가능)
     * <p>
     * 실무 시 추가 고려사항:
     * - Toss-Signature 헤더 검증으로 위조 요청 차단
     * - 이미 처리된 이벤트는 무시 (멱등성)
     */
    @PostMapping("/tosspayments")
    public ResponseEntity<Void> handleTossWebhook(@RequestBody TossWebhookRequest request) {

        log.info("[Webhook] 토스 이벤트 수신 — eventType: {}, paymentKey: {}",
                request.getEventType(), request.getPaymentKey());

        // 멱등성 처리 — 이미 우리 DB에 저장된 paymentKey면 중복 이벤트이므로 무시
        if ("PAYMENT_STATUS_CHANGED".equals(request.getEventType())) {
            boolean alreadyProcessed = paymentRepository
                    .findByPaymentKey(request.getPaymentKey())
                    .isPresent();

            if (alreadyProcessed) {
                log.info("[Webhook] 중복 이벤트 무시 — paymentKey: {}", request.getPaymentKey());
                return ResponseEntity.ok().build();
            }

            // 필요 시 추가 처리 로직 확장 가능
            // (예: 가상계좌 입금 완료 이벤트 처리 등)
        }

        return ResponseEntity.ok().build();
    }
}
