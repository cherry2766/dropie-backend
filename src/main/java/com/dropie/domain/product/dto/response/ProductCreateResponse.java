package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import com.dropie.domain.tag.dto.response.TagResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// POST 201 응답 — { id, name, stock }
@Getter
@Builder
public class ProductCreateResponse {

    private Long id;
    private String name;
    private int stock;
    private List<TagResponse> tags;

    public static ProductCreateResponse from(Product product) {
        return ProductCreateResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .stock(product.getStock())
                .tags(product.getProductTags().stream()
                        .map(pt -> TagResponse.from(pt.getTag()))
                        .toList())
                .build();
    }
}
