package com.dropie.domain.event.entity;

public enum EventStatus {
    UPCOMING, OPEN, SOLD_OUT, CLOSED, FINISHED;

    // 현재 상태에서 target 상태로 전환이 허용되는지 검사
    // - 스케줄러: UPCOMING→OPEN, OPEN→CLOSED, CLOSED→OPEN(endAt이 미래로 늘어난 경우)
    // - 주문 시: OPEN→SOLD_OUT (전 상품 품절), SOLD_OUT→OPEN (취소로 재고 복구)
    // - 관리자: 어느 상태에서든 FINISHED로 강제 종료 가능
    public boolean canTransitionTo(EventStatus target) {
        return switch (this) {
            case UPCOMING -> target == OPEN || target == FINISHED;
            case OPEN     -> target == CLOSED || target == SOLD_OUT || target == FINISHED;
            case CLOSED   -> target == OPEN || target == FINISHED;
            case SOLD_OUT -> target == OPEN || target == CLOSED || target == FINISHED;
            case FINISHED -> false;
        };
    }
}
