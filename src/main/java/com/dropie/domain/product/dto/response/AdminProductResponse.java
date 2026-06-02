package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import com.dropie.domain.tag.dto.response.TagResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// GET /admin/products 관리자 상품 목록 응답 DTO
// 일반 사용자용 ProductResponse와 달리 어느 이벤트 상품인지 알아야 하므로 eventId, brandName 포함
@Getter
@Builder
public class AdminProductResponse {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private int price;
    private int stock;
    private Long eventId;
    private String brandName;
    private List<TagResponse> tags;

    public static AdminProductResponse from(Product product) {
        return AdminProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .stock(product.getStock())
                .eventId(product.getEvent().getId())
                .brandName(product.getEvent().getBrandName())
                .tags(product.getProductTags().stream()
                        .map(pt -> TagResponse.from(pt.getTag()))
                        .toList())
                .build();
    }
}
