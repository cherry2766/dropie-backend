package com.dropie.domain.event.dto.response;

import com.dropie.domain.event.event.StockChangedEvent;

import java.time.LocalDateTime;

// WebSocket으로 전송되는 메시지 형식
// 프론트는 이 구조 그대로 파싱
public record StockUpdateMessage(
        Long eventId,
        Long productId,
        int stock,
        LocalDateTime occurredAt
) {
    public static StockUpdateMessage from(StockChangedEvent event) {
        return new StockUpdateMessage(
                event.eventId(),
                event.productId(),
                event.currentStock(),
                event.occurredAt()
        );
    }
}
