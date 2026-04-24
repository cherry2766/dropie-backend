package com.dropie.domain.order.dto.response;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 목록 조회용
@Getter
@Builder
public class OrderResponse {

    private Long orderId;
    private String orderNumber;

    // 대표 브랜드명 (첫 번째 OrderItem의 브랜드)
    // 다중 브랜드 주문은 현재 스펙상 대표값만 노출
    private String brandName;

    private int totalPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .brandName(order.getRepresentativeBrandName())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
