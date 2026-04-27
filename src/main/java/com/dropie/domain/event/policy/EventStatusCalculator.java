package com.dropie.domain.event.policy;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;

import java.time.LocalDateTime;

// 응답 시점에 화면에 보여줄 status를 계산하는 순수 함수
public final class EventStatusCalculator {

    private EventStatusCalculator() {}

    /**
     * 우선순위:
     *   1. FINISHED → FINISHED         (관리자 강제 종료, 최우선)
     *   2. CLOSED   → CLOSED           (관리자 일시 중단 의도 존중)
     *   3. 전 상품 품절 → SOLD_OUT
     *   4. now > endAt → CLOSED
     *   5. startAt ≤ now ≤ endAt → OPEN
     *   6. now < startAt → UPCOMING
     */
    public static EventStatus resolve(Event event, LocalDateTime now, boolean allSoldOut) {
        EventStatus dbStatus = event.getStatus();

        // 관리자 강제 상태가 최우선 — 시간/재고와 무관하게 그대로 노출
        if (dbStatus == EventStatus.FINISHED) return EventStatus.FINISHED;
        if (dbStatus == EventStatus.CLOSED) return EventStatus.CLOSED;

        // 전 상품 품절이 시간보다 우선
        if (allSoldOut) return EventStatus.SOLD_OUT;

        // 시간 기반 판단
        if (now.isAfter(event.getEndAt())) return EventStatus.CLOSED;
        if (!now.isBefore(event.getStartAt())) return EventStatus.OPEN;
        return EventStatus.UPCOMING;
    }
}
