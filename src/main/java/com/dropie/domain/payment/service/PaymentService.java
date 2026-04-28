package com.dropie.domain.payment.service;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.event.OrderPaidEvent;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.payment.client.TossPaymentClient;
import com.dropie.domain.payment.dto.request.PaymentConfirmRequest;
import com.dropie.domain.payment.dto.response.PaymentConfirmResponse;
import com.dropie.domain.payment.dto.response.TossConfirmResponse;
import com.dropie.domain.payment.entity.Payment;
import com.dropie.domain.payment.repository.PaymentRepository;
import com.dropie.domain.product.entity.Product;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private static final String PENDING_ORDER_KEY_PREFIX = "pending_order:";

    /**
     * 토스 결제 확인(승인) 처리
     * <p>
     * 처리 순서:
     * 1. 주문 조회 (비관적 락) + 본인 주문 확인
     * 2. 멱등성 체크 — 이미 PAID면 기존 결과 반환 (중복 요청 방어)
     * 3. PENDING 상태 확인 (CANCELED된 주문은 결제 불가)
     * 4. 금액 검증 — 프론트에서 변조했을 가능성 방어
     * 5. 자동 취소 TTL 키 선삭제 — 만료 이벤트 발화 차단
     * 6. 토스 승인 API 호출 (실패 시 자동 롤백)
     * 7. 주문 PAID + Payment 저장
     */
    @Transactional
    public PaymentConfirmResponse confirmPayment(String email, Long orderId, PaymentConfirmRequest request) {
        // 서비스 진입 로그 INFO: 컨트롤러 → 서비스 호출 연결 확인
        log.info("[confirmPayment] 진입 - email={}, orderId={}, amount={}",
                email, orderId, request.getAmount());

        // 1. 주문 조회
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> {
                    // WARN: 존재하지 않는 주문에 대한 결제 시도 (프론트 버그 or 악의적 요청 의심)
                    log.warn("[confirmPayment] 주문 없음 - orderId={}", orderId);
                    return new OrderNotFoundException();
                });


        log.debug("[confirmPayment] 주문 조회 완료 - orderId={}, status={}, totalPrice={}, ownerEmail={}",
                order.getId(), order.getStatus(), order.getTotalPrice(), order.getUser().getEmail());

        // 본인 주문인지 확인 - 다른 사람의 주문 결제 시도 방어
        if (!order.getUser().getEmail().equals(email)) {
            // WARN: 타인의 주문을 결제하려 한 이상 접근
            log.warn("[confirmPayment] 본인 주문 아님 - requesterEmail={}, ownerEmail={}",
                    email, order.getUser().getEmail());
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        // 2. 멱등성 체크 - 이미 결제 완료된 주문이면 기존 결제 정보를 그대로 반환
        // 프론트가 실수로 confirm을 두 번 보내도 안전하게 처리됨
        if (order.getStatus() == OrderStatus.PAID) {
            Payment existingPayment = paymentRepository.findByOrder(order)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED));
            log.info("[confirmPayment] 이미 처리된 결제 재요청 - orderId={}", orderId);
            return PaymentConfirmResponse.of(order, existingPayment);
        }

        // 3. PENDING 상태 확인 (CANCELED 등 다른 상태면 결제 불가)
        if (order.getStatus() != OrderStatus.PENDING) {
            // WARN: 취소됐거나 이상 상태의 주문에 대한 결제 시도
            log.warn("[confirmPayment] PENDING 아님 - orderId={}, currentStatus={}",
                    orderId, order.getStatus());
            throw new BusinessException(ErrorCode.ORDER_NOT_PENDING);
        }

        // 4. 금액 검증 - 클라이언트 측 금액 변조 공격 방어
        if (order.getTotalPrice() != request.getAmount()) {
            // WARN: 프론트에서 변조됐거나 프론트 계산 버그 가능성
            log.warn("[confirmPayment] 금액 불일치 - orderId={}, expected={}, actual={}",
                    orderId, order.getTotalPrice(), request.getAmount());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        log.debug("[confirmPayment] 금액 검증 통과 - amount={}", order.getTotalPrice());

        // 5. 자동 취소 TTL 키 삭제
        // → 결제 확정 흐름에 들어왔으므로 만료 이벤트가 더는 발화되지 않게 선제 차단
        // → 만약 여기 도달 직전에 이미 만료 이벤트가 발행됐더라도, autoCancelIfPending() 쪽도
        //    동일한 findByIdForUpdate() 비관적 락을 사용하므로 같은 row 경합이 직렬화되어 안전
        redisTemplate.delete(PENDING_ORDER_KEY_PREFIX + orderId);
        log.debug("[confirmPayment] 자동 취소 TTL 키 삭제 - orderId={}", orderId);

        // 6. 토스 결제 승인 API 호출
        // 실패 시 try-catch 안에서 주문 취소 + 재고 복원
        log.info("[confirmPayment] 토스 승인 API 호출 시작 - orderNumber={}, amount={}",
                order.getOrderNumber(), request.getAmount());
        TossConfirmResponse tossResponse;
        try {
            tossResponse = tossPaymentClient.confirm(
                    request.getPaymentKey(),
                    order.getOrderNumber(),  // 토스에 넘긴 orderId = 우리 orderNumber
                    request.getAmount()
            );
        } catch (BusinessException e) {
            // 토스 API 실패 -> 선점했던 재고 복원, 주문 취소
            rollbackOrder(order);
            throw e;
        }
        log.info("[confirmPayment] 토스 승인 API 응답 수신 - paymentKey={}, method={}, approvedAt={}",
                tossResponse.getPaymentKey(), tossResponse.getMethod(), tossResponse.getApprovedAt());

        // 7. 주문 상태 PAID로 변경 + 결제 정보 저장
        order.confirm();  // PENDING → PAID

        Payment payment = Payment.builder()
                .order(order)
                .paymentKey(tossResponse.getPaymentKey())
                .amount(tossResponse.getTotalAmount())
                .method(tossResponse.getMethod())
                .approvedAt(parseApprovedAt(tossResponse.getApprovedAt()))
                .build();
        paymentRepository.save(payment);

        log.info("[confirmPayment] 결제 완료 - orderId={}, paymentKey={}, amount={}",
                orderId, payment.getPaymentKey(), payment.getAmount());

        // 결제 완료 도메인 이벤트
        // 한 주문에 여러 이벤트의 상품이 섞여있을 수 있음 → 각 이벤트별로 발행
        order.getOrderItems().stream()
                .map(item -> item.getProduct().getEvent().getId())
                .distinct()
                .forEach(eventId -> eventPublisher.publishEvent(
                        new OrderPaidEvent(order.getId(), order.getUser().getId(), eventId)
                ));

        return PaymentConfirmResponse.of(order, payment);
    }

    /**
     * 결제 실패 시 롤백 처리
     *
     * 주문 생성(POST /orders) 단계에서 재고를 이미 감소시켰으므로,
     * 결제 실패 시 반드시 재고를 되돌려야 다음 구매자가 살 수 있음.
     */
    private void rollbackOrder(Order order) {
        // 주문 취소 (PENDING → CANCELED)
        order.cancel();

        // 주문 아이템별로 선점했던 재고 복원
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.increaseStock(item.getQuantity());
        }

        log.warn("[PaymentService] 결제 실패로 주문 취소 + 재고 복원 — orderId: {}", order.getId());
    }

    /**
     * 토스 응답의 approvedAt을 LocalDateTime으로 변환
     * 토스 형식: "2026-04-21T12:00:00+09:00" (ISO 8601 오프셋 포함)
     */
    private LocalDateTime parseApprovedAt(String approvedAt) {
        return OffsetDateTime.parse(approvedAt).toLocalDateTime();
    }
}
