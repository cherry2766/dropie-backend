package com.dropie.domain.product;

import com.dropie.domain.common.BaseEntity;
import com.dropie.domain.event.Event;
import com.dropie.domain.order.OrderItem;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
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

    @OneToMany(mappedBy = "product")
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "product")
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
}
