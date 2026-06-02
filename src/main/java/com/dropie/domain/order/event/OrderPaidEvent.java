package com.dropie.domain.order.event;

// 결제가 PAID로 커밋된 후 발행되는 도메인 이벤트
//
// 이 이벤트는 두 곳에서 구독:
//   1) PopularityScoreListener      — 인기 점수 +5
//   2) (AI 추천 가이드) TasteTagAccumulator — 사용자 취향 태그 점수 누적
public record OrderPaidEvent(
        Long orderId,
        Long userId,
        Long eventId
) { }
