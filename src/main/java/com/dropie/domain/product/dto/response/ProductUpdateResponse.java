package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

// PATCH 200 응답 — { id, name, price }
@Getter
@Builder
public class ProductUpdateResponse {

    private Long id;
    private String name;
    private int price;

    public static ProductUpdateResponse from(Product product) {
        return ProductUpdateResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .build();
    }
}
