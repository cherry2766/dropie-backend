package com.dropie.domain.product.entity;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.global.common.BaseEntity;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.OutOfStockException;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String name;

    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    // @Version: 낙관적 락을 위한 버전 관리 컬럼
    // JPA가 UPDATE 쿼리에 자동으로 "WHERE version = :현재버전" 조건을 붙임
    // 두 트랜잭션이 동시에 같은 version을 읽고 UPDATE를 시도하면,
    // 먼저 커밋한 쪽이 version을 올려버려 나중 쪽은 조건이 0건 → ObjectOptimisticLockingFailureException 발생
    @Version
    private Long version;

    // @Builder.Default가 없으면 Lombok @Builder가 = new ArrayList<>() 초기값을 무시해
    // builder()로 만든 객체는 null이 됨 → product.getProductTags().stream() 호출 시 NPE
    @OneToMany(mappedBy = "product")
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @Builder.Default
    private List<ProductTag> productTags = new ArrayList<>();

    // PATCH 부분 업데이트 — null이면 기존 값 유지
    // price는 int라 null이 없으므로 파라미터를 Integer로 받아야 함
    public void update(String name, String imageUrl, String description, Integer price) {
        if (name != null)        this.name = name;
        if (imageUrl != null)    this.imageUrl = imageUrl;
        if (description != null) this.description = description;
        if (price != null)       this.price = price;
    }

    // 재고 수정 — 음수 방어 (잘못된 입력값이므로 400)
    public void updateStock(int stock) {
        if (stock < 0) throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        this.stock = stock;
    }

    // 주문 시 재고 차감 — 재고 부족이면 OUT_OF_STOCK 예외
    // updateStock()과 구분: 이건 n개 차감, updateStock()은 n으로 직접 설정
    public void decreaseStock(int quantity) {
        if(this.stock < quantity) {
            throw new OutOfStockException();
        }
        this.stock -= quantity;
    }

    // 주문 취소 시 재고 복구 — 차감된 수량만큼 다시 증가
    public void increaseStock(int quantity) {
        this.stock += quantity;
    }

}
