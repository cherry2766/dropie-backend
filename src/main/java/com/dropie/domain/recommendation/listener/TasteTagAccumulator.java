package com.dropie.domain.recommendation.listener;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.event.OrderPaidEvent;
import com.dropie.domain.order.repository.OrderRepository;
import com.dropie.domain.product.entity.ProductTag;
import com.dropie.domain.recommendation.service.TasteTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.stream.Collectors;

// 결제 완료 → 사용자의 주문 상품 태그를 ZSET에 누적
//
// 흐름:
//   1) AFTER_COMMIT — 실제로 PAID 커밋된 후에만 동작 (롤백된 주문엔 누적 X)
//   2) @Async      — 결제 응답 속도에 영향 없음
//   3) REQUIRES_NEW 트랜잭션 안에서 동작 → lazy 컬렉션(productTags) 접근 가능
//      → Order/OrderItems/Product까지 fetch join, productTags는 @BatchSize로 IN 절 일괄 로딩
//        (Order.orderItems + Product.productTags 두 List를 동시 fetch join하면
//         MultipleBagFetchException 발생하므로 productTags는 lazy로 분리)
//   4) tagId 추출  → TasteTagService.addTagScores 위임
@Slf4j
@Component
@RequiredArgsConstructor
public class TasteTagAccumulator {

    private final OrderRepository orderRepository;
    private final TasteTagService tasteTagService;

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // AFTER_COMMIT 리스너에 @Transactional을 붙이려면 propagation 명시 필수
    //   - 이미 결제 트랜잭션은 커밋 완료 + @Async라 별도 스레드 실행
    //   - REQUIRES_NEW: 새 트랜잭션을 시작해 lazy 컬렉션(productTags 등) 순회 가능
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            Order order = orderRepository.findByIdWithItemsForTagAccumulation(event.orderId())
                    .orElse(null);
            if (order == null) {
                log.warn("[TasteTagAccumulator] order 없음 - orderId={}", event.orderId());
                return;
            }

            // 같은 태그가 여러 상품에 걸리면 그만큼 누적 — "그 취향이 강하다"로 해석 (의도된 동작)
            List<Long> tagIds = order.getOrderItems().stream()
                    .map(item -> item.getProduct())
                    .flatMap(p -> p.getProductTags().stream())
                    .map(ProductTag::getTag)
                    .map(t -> t.getId())
                    .collect(Collectors.toList());

            tasteTagService.addTagScores(event.userId(), tagIds);
        } catch (Exception e) {
            // 추천 데이터 누적 실패가 결제 자체에 영향 가면 안 됨
            log.error("[TasteTagAccumulator] 누적 실패 - userId={}, orderId={}",
                    event.userId(), event.orderId(), e);
        }
    }
}
