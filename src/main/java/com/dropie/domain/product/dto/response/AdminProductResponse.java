package com.dropie.domain.product.dto.response;

import com.dropie.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

// GET /admin/products 관리자 상품 목록 응답 DTO
// 일반 사용자용 ProductResponse와 달리 어느 이벤트 상품인지 알아야 하므로 eventId, brandName 포함
@Getter
@Builder
public class AdminProductResponse {

    private Long id;
    private String name;
    private String imageUrl;
    private int price;
    private int stock;
    private Long eventId;
    private String brandName; // 이벤트 브랜드명 — 관리자 목록에서 어느 이벤트 소속인지 식별

    public static AdminProductResponse from(Product product) {
        return AdminProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .stock(product.getStock())
                .eventId(product.getEvent().getId())
                .brandName(product.getEvent().getBrandName())
                .build();
    }
}
