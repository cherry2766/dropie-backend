package com.dropie.domain.order.dto.response;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

// 주문 취소 후 반환
@Getter
@Builder
public class OrderCancelResponse {

    private Long orderId;
    private OrderStatus status;

    public static OrderCancelResponse from(Order order) {
        return OrderCancelResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .build();
    }
}
