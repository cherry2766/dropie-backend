package com.dropie.domain.event.listener;

import com.dropie.domain.event.dto.response.StockUpdateMessage;
import com.dropie.domain.event.event.StockChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 도메인 이벤트(StockChangedEvent)를 받아 WebSocket으로 브로드캐스트하는 리스너
//
// 핵심 어노테이션:
//   @TransactionalEventListener(phase = AFTER_COMMIT)
//     → 발행 트랜잭션이 "커밋된 직후"에만 호출됨
//     → 롤백되면 호출 자체가 안 됨 (잘못된 정보 전송 방지)
//   @Async("domainEventExecutor")
//     → 별도 ThreadPool에서 실행 → WebSocket 전송 지연/실패가 호출 트랜잭션에 영향 X
// WebSocketConfig가 꺼지면(예: 테스트) SimpMessagingTemplate이 없어 빈 생성에 실패하므로,
// 리스너도 동일한 토글에 묶어서 함께 활성/비활성되게 한다.
@Slf4j
@Component
@ConditionalOnProperty(
        name = "dropie.realtime.websocket.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class StockChangedEventListener {

    // STOMP 메시지 발행기
    // convertAndSend(destination, payload) → JSON 직렬화 후 토픽으로 전송
    private final SimpMessagingTemplate messagingTemplate;

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        // destination에 eventId를 끼워넣어 이벤트 단위로 채널을 분리
        String destination = "/topic/events/" + event.eventId() + "/stock";

        StockUpdateMessage payload = StockUpdateMessage.from(event);

        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("[StockChangedEventListener] 전송 완료 - dest={}, productId={}, stock={}",
                    destination, event.productId(), event.currentStock());
        } catch (Exception e) {
            // 전송 실패가 주문 트랜잭션에 영향이 가면 안 됨 → 로그만 남기고 끝
            // (브로커 장애, 직렬화 오류 등)
            log.error("[StockChangedEventListener] 전송 실패 - dest={}, productId={}",
                    destination, event.productId(), e);
        }
    }
}
