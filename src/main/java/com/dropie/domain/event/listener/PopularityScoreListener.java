package com.dropie.domain.event.listener;

import com.dropie.domain.event.service.PopularEventService;
import com.dropie.domain.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 결제 완료 도메인 이벤트를 받아 인기 점수를 누적
// AFTER_COMMIT: 결제가 실제로 PAID로 커밋된 후에만 점수 반영
// @Async: Redis 응답 지연이 결제 응답을 늦추지 않게 격리
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularityScoreListener {

    private final PopularEventService popularEventService;

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            popularEventService.addScore(event.getEventId(), PopularEventService.ORDER_SCORE);
        } catch (Exception e) {
            log.warn("[PopularityScoreListener] 점수 누적 실패 - eventId={}", event.getEventId(), e);
        }
    }
}
