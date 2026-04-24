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

        // 상품 이미지 URL — 주문 상세에서 썸네일 표시용
        private String imageUrl;

        // 상품이 속한 이벤트의 브랜드명
        // → 다중 브랜드 주문에서도 각 item이 어느 브랜드 소속인지 구분 가능
        private String brandName;

        private int quantity;
        private int orderPrice;

        public static OrderItemDetail from(OrderItem item) {
            return OrderItemDetail.builder()
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .imageUrl(item.getProduct().getImageUrl())
                    // Repository에서 Event까지 fetch join해서 N+1 방지
                    .brandName(item.getProduct().getEvent().getBrandName())
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
