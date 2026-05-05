package com.dropie.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "주문 생성 요청")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @Schema(description = "수령인", example = "강체리", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String receiverName;

    @Schema(description = "연락처", example = "010-1234-5678", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String phone;

    @Schema(description = "우편번호", example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String zipcode;

    @Schema(description = "기본 주소", example = "서울시 강남구 테헤란로 1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String address1;

    @Schema(description = "상세 주소", example = "501호")
    private String address2;

    @Schema(description = "주문 상품 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "주문할 상품을 선택해주세요.")
    @Valid // 리스트 내부 객체도 검증하려면 @Valid 필요
    private List<OrderItemRequest> items;

    // 주문 상품 요청 — 내부 정적 클래스로 관리
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @Schema(description = "상품 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        private Long productId;

        @Schema(description = "수량", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        // @Positive: 0 이하 수량 방어 → INVALID_QUANTITY와 별개로 입력값 자체를 막음
        @Positive(message = "수량은 1개 이상 선택해주세요.")
        private int quantity;
    }
}
