package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

// POST 201 응답 — { id, name, stock }
@Getter
@Builder
public class ProductCreateResponse {

    private Long id;
    private String name;
    private int stock;

    public static ProductCreateResponse from(Product product) {
        return ProductCreateResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .stock(product.getStock())
                .build();
    }
}
