package com.dropie.domain.payment.entity;

import com.dropie.domain.order.entity.Order;
import com.dropie.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주문과 1:1 관계 — 하나의 주문은 하나의 결제만 가짐
    // LAZY: Payment를 조회할 때 Order를 즉시 로드하지 않아 성능 최적화
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 토스페이먼츠가 발급하는 고유 결제 키
    // 환불이나 조회 시 이 값이 필요함
    @Column(nullable = false, unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    // "카드", "가상계좌", "간편결제" 등 토스 응답에서 그대로 저장
    private String method;

    // 토스에서 실제 승인이 완료된 시각
    // OffsetDateTime("2026-04-21T12:00:00+09:00") → LocalDateTime으로 변환해서 저장
    private LocalDateTime approvedAt;

    @Builder
    public Payment(Order order, String paymentKey, int amount, String method, LocalDateTime approvedAt) {
        this.order = order;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.method = method;
        this.approvedAt = approvedAt;
    }
}
