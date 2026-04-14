package com.dropie.domain.order.dto.response;

import com.dropie.domain.order.entity.Order;
import com.dropie.domain.order.entity.OrderItem;
import com.dropie.domain.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 상세 조회용
@Getter
@Builder
public class OrderDetailResponse {

    private Long orderId;
    private String orderNumber;
    private String receiverName;
    private String phone;
    private String address;      // address1 + address2 합산 문자열
    private int totalPrice;
    private OrderStatus status;
    private List<OrderItemDetail> items;

    // 주문 상세 내부 상품 정보
    @Getter
    @Builder
    public static class OrderItemDetail {
        private Long productId;
        private String productName;
        private int quantity;
        private int orderPrice;

        public static OrderItemDetail from(OrderItem item) {
            return OrderItemDetail.builder()
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .quantity(item.getQuantity())
                    .orderPrice(item.getOrderPrice())
                    .build();
        }
    }

    public static OrderDetailResponse from(Order order) {
        // address2가 null이거나 빈 값이면 address1만, 아니면 공백으로 합침
        String address = (order.getAddress2() != null && !order.getAddress2().isBlank())
                ? order.getAddress1() + " " + order.getAddress2()
                : order.getAddress1();

        List<OrderItemDetail> items = order.getOrderItems().stream()
                .map(OrderItemDetail::from)
                .toList();

        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .receiverName(order.getReceiverName())
                .phone(order.getPhone())
                .address(address)
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .items(items)
                .build();
    }
}
