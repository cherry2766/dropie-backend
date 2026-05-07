package com.dropie.domain.order.listener;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import com.dropie.domain.order.event.OrderRollbackEvent;
import com.dropie.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 결제 실패 보상 트랜잭션 처리 — AFTER_ROLLBACK 트랜잭션 이벤트 리스너
//
// 동작 흐름:
// 1. PaymentService.confirmPayment()가 토스 API 실패로 catch 블록 진입
// 2. catch에서 OrderRollbackEvent 발행 + throw로 트랜잭션 롤백 트리거
// 3. 외부 트랜잭션이 완전히 롤백되며 비관적 락 해제 (← 핵심)
// 4. AFTER_ROLLBACK 페이즈에 이 리스너가 fire
// 5. REQUIRES_NEW 별도 트랜잭션에서 주문 취소 + 재고 복원 후 커밋
//
// 왜 이렇게 복잡하게?
// 같은 트랜잭션 안에서 catch 후 dirty-checking으로 처리하면 throw e가 트랜잭션을
// 롤백시키면서 변경도 함께 무효화됨. 또한 REQUIRES_NEW를 catch 직접 호출하면
// 외부 트랜잭션의 비관적 락(findByIdForUpdate)과 새 트랜잭션의 UPDATE가
// 같은 row에서 충돌해 deadlock 위험. AFTER_ROLLBACK은 외부 락 해제 후 동작이므로 안전.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderRollbackEventListener {

    private final OrderRepository orderRepository;

    // @TransactionalEventListener: 트랜잭션 라이프사이클에 맞춰 이벤트 처리
    // phase = AFTER_ROLLBACK: 외부 트랜잭션 롤백 직후 실행 → 락이 이미 해제된 시점
    //
    // @Transactional(REQUIRES_NEW): 외부 트랜잭션이 이미 롤백되어 사라졌으므로 새 트랜잭션 필요
    // (REQUIRED만 쓰면 트랜잭션이 없는 상태로 실행되어 dirty-checking이 동작 안 함)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleRollback(OrderRollbackEvent event) {
        Long orderId = event.orderId();

        // OrderItems + Product를 fetch join — 재고 복원하려면 Product가 필요
        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);

        if (order == null) {
            log.warn("[OrderRollback] 주문 없음 - orderId={}", orderId);
            return;
        }

        // 멱등 처리: 이미 CANCELED면 중복 처리 방지
        // (자동 취소 스케줄러·만료 리스너와 race가 일어날 수 있음)
        if (order.getStatus() == OrderStatus.CANCELED) {
            log.debug("[OrderRollback] 이미 취소됨 - orderId={}", orderId);
            return;
        }

        // PENDING이 아니면 보상 불가 — PAID 상태면 결제는 성공한 것
        // (이 분기에 들어올 일은 거의 없지만 방어 코드)
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[OrderRollback] PENDING 아님, 보상 스킵 - orderId={}, status={}",
                    orderId, order.getStatus());
            return;
        }

        // 주문 취소 + 모든 OrderItem의 상품 재고 복원
        order.cancel();
        for (OrderItem item : order.getOrderItems()) {
            item.getProduct().increaseStock(item.getQuantity());
        }

        log.warn("[OrderRollback] 보상 트랜잭션 — 주문 취소 + 재고 복원 (orderId={})", orderId);
    }
}
