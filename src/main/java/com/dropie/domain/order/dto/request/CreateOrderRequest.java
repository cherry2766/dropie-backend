package com.dropie.domain.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 주문 생성 요청
// @NoArgsConstructor: Jackson이 JSON → 객체 변환 시 기본 생성자 필요
// @Builder + @AllArgsConstructor: 테스트에서 직접 객체 생성 시 사용
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank
    private String receiverName;

    @NotBlank
    private String phone;

    @NotBlank
    private String zipcode;

    @NotBlank
    private String address1;

    private String address2;

    @NotEmpty(message = "주문할 상품을 선택해주세요.")
    @Valid // 리스트 내부 객체도 검증하려면 @Valid 필요
    private List<OrderItemRequest> items;

    // 주문 상품 요청 — 내부 정적 클래스로 관리
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull
        private Long productId;

        // @Positive: 0 이하 수량 방어 → INVALID_QUANTITY와 별개로 입력값 자체를 막음
        @Positive(message = "수량은 1개 이상 선택해주세요.")
        private int quantity;
    }
}
