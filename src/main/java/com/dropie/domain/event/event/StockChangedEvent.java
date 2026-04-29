package com.dropie.domain.event.event;

import java.time.LocalDateTime;

// 재고가 변경되었음을 알리는 도메인 이벤트
//
// 발행 위치:
//   - OrderService.createOrder      → 주문 생성으로 재고 차감
//   - OrderService.cancelOrder      → 사용자 취소로 재고 복구
//   - PendingOrderAutoCancelService → TTL 만료로 자동 취소되어 재고 복구
//
// 단일 상품 단위로 발행 (구독자가 productId로 골라 받기 쉽게)
public record StockChangedEvent(
        Long eventId,        // 어떤 이벤트인지 (구독 토픽 구분용)
        Long productId,      // 어떤 상품의 재고가 바뀌었는지
        int currentStock,    // 변경 후 stock (절대값으로 보냄 → 클라이언트는 그대로 화면에 반영)
        LocalDateTime occurredAt
) {
    public static StockChangedEvent of(Long eventId, Long productId, int currentStock) {
        return new StockChangedEvent(eventId, productId, currentStock, LocalDateTime.now());
    }
}
