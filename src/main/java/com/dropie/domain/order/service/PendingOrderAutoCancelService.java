package com.dropie.domain.order.service;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.product.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// PENDING 주문의 자동 취소 전용 서비스
// → 유저 요청 경로(cancelOrder)와 분리한 이유:
//   1) userDetails 검증이 불필요 (시스템이 주체)
//   2) 상태 재검증 로직이 다르다 (PENDING일 때만)
//   3) 배치와 리스너에서 공통으로 사용
//
// 케이스 ④(토스 결제는 성공했는데 confirm 응답을 못 받고 이탈한 주문)에 대한 토스 역조회 +
// PAID 복구 로직은 v2 확장 항목으로 분리. 본 클래스는 1차 방어(TTL 자동 취소)에 집중한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOrderAutoCancelService {

    private final OrderRepository orderRepository;

    /**
     * orderId가 여전히 PENDING 상태라면 자동으로 취소하고 재고를 복구한다.
     * <p>
     * 동시성 방어:
     * - 트랜잭션 안에서 findByIdForUpdate()로 비관적 락 획득 → confirmPayment와 같은 row를 다툰 경우
     *   한쪽이 끝날 때까지 대기 후 최신 상태 조회
     * - 락 획득 후 상태가 PENDING이 아니면(예: 이미 PAID) 아무것도 하지 않음 → 멱등
     *
     * @param orderId 자동 취소 대상 주문 id
     */
    @Transactional
    public void autoCancelIfPending(Long orderId) {
        // 비관적 락으로 주문 조회 - 같은 주문에 대한 동시 요청을 직렬화
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        // 주문이 이미 삭제됐거나 존재하지 않는 경우 — 리스너 이벤트는 결과 확인 없이 그냥 실행만 하는 방식이라 정상 케이스
        if (order == null) {
            log.debug("[autoCancel] 주문 없음 - orderId={}, 무시", orderId);
            return;
        }

        // 이미 PAID, CANCELED 등 다른 상태로 전이됐으면 아무것도 안 함 (멱등)
        // → 예: 사용자가 TTL 만료 직전에 결제를 완료한 경우 confirmPayment가 먼저 락을 획득하고 PAID로 바꿨을 것
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[autoCancel] PENDING 아님, 스킵 - orderId={}, status={}",
                    orderId, order.getStatus());
            return;
        }

        // 주문 취소 + 재고 복구
        order.cancel(); // PENDING → CANCELED
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.increaseStock(item.getQuantity());  // 차감됐던 만큼 복구
            log.debug("[autoCancel] 재고 복구 - productId={}, +{}",
                    product.getId(), item.getQuantity());
        }

        log.info("[autoCancel] 자동 취소 완료 - orderId={}, orderNumber={}",
                orderId, order.getOrderNumber());
    }
}
