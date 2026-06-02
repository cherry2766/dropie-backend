package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import com.dropie.domain.tag.dto.response.TagResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// PATCH 200 응답 — { id, name, price }
@Getter
@Builder
public class ProductUpdateResponse {

    private Long id;
    private String name;
    private int price;
    private List<TagResponse> tags;

    public static ProductUpdateResponse from(Product product) {
        return ProductUpdateResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .tags(product.getProductTags().stream()
                        .map(pt -> TagResponse.from(pt.getTag()))
                        .toList())
                .build();
    }
}
