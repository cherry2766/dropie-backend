package com.dropie.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 재고 수정 전용 DTO — stock 하나만
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductStockRequest {

    @NotNull
    private Integer stock;
}
