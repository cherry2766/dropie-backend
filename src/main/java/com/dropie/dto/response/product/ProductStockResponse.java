package com.dropie.dto.response.product;

import com.dropie.domain.product.Product;
import lombok.Builder;
import lombok.Getter;

// 재고 수정 200 응답 — { id, stock }
@Getter
@Builder
public class ProductStockResponse {

    private Long id;
    private int stock;

    public static ProductStockResponse from(Product product) {
        return ProductStockResponse.builder()
                .id(product.getId())
                .stock(product.getStock())
                .build();
    }
}
