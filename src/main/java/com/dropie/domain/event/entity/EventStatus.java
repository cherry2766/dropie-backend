package com.dropie.domain.event.entity;

public enum EventStatus {
    UPCOMING, OPEN, CLOSED, FINISHED;

    // 현재 상태에서 target 상태로 전환이 허용되는지 검사
    // UPCOMING → OPEN, FINISHED
    // OPEN     → CLOSED, FINISHED
    // CLOSED   → FINISHED
    // FINISHED → 전환 불가 (종료 후 되돌릴 수 없음)
    public boolean canTransitionTo(EventStatus target) {
        return switch (this) {
            case UPCOMING -> target == OPEN || target == FINISHED;
            case OPEN -> target == CLOSED || target == FINISHED;
            case CLOSED -> target == OPEN || target == FINISHED;
            case FINISHED -> false;
        };
    }
}
