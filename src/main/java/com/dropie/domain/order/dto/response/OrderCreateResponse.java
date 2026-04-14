package com.dropie.domain.order.dto.response;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

// 주문 생성 완료 후 반환
@Getter
@Builder
public class OrderCreateResponse {

    private Long orderId;
    private String orderNumber;
    private int totalPrice;
    private OrderStatus status;

    public static OrderCreateResponse from(Order order) {
        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .build();
    }
}
