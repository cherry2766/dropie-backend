package com.dropie.domain.order.entity;

import com.dropie.domain.user.entity.User;
import com.dropie.global.common.BaseEntity;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String zipcode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    @Column(nullable = false)
    private int totalPrice;

    private String deliveryMemo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // CascadeType.ALL: Order 저장 시 OrderItem도 함께 저장/삭제됨
    // orphanRemoval: Order에서 제거된 OrderItem은 DB에서도 삭제됨
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default // @Builder와 함께 쓸 때 초기값 유지하려면 필요
    private List<OrderItem> orderItems = new ArrayList<>();

    // 주문 취소 — 취소 불가 상태면 CANCEL_NOT_ALLOWED 예외
    // 상태 검증을 Service가 아닌 Entity에서 처리 (도메인 로직을 한 곳에 모음)
    public void cancel() {
        if(!this.status.canCancel()) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
        }
        this.status = OrderStatus.CANCELED;
    }

    // 주문 상품 추가 — OrderItem을 컬렉션에 추가
    // CascadeType.ALL이 있으므로 Order 저장 시 함께 INSERT됨
    public void addOrderItem(OrderItem item) {
        this.orderItems.add(item);
    }

    // 최종 총 금액 반영 — 주문 상품 루프가 끝난 후 계산된 금액을 덮어씀
    public void updateTotalPrice(int totalPrice) {
        this.totalPrice = totalPrice;
    }
}
