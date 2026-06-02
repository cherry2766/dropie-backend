package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

// GET /events/{eventId} 상세 응답 내 상품 목록용 DTO
@Getter
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String imageUrl;
    private String description;
    private int price;
    private int stock;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
    }
}
