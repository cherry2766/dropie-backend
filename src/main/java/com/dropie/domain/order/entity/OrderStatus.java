package com.dropie.domain.order.entity;

public enum OrderStatus {
    PENDING,    // 주문 접수 (결제 전)
    PAID,       // 결제 완료
    CANCELED,   // 취소됨
    COMPLETED;  // 구매 확정

    // 취소 가능 여부 — PENDING, PAID 상태만 취소 가능
    // CANCELED/COMPLETED는 이미 최종 상태이므로 취소 불가
    public boolean canCancel() {
        return this == PENDING || this == PAID;
    }
}
