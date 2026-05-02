package com.dropie.domain.recommendation.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecommendationResponse {
    private Long eventId;
    private String brandName;
    private String thumbnailImageUrl;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long productId;
    private String productName;
    private int productPrice;
    private String productImageUrl;
    private String message;

    public static RecommendationResponse of(Event event, Product product, String message) {
        return RecommendationResponse.builder()
                .eventId(event.getId())
                .brandName(event.getBrandName())
                .thumbnailImageUrl(event.getThumbnailImageUrl())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .productId(product.getId())
                .productName(product.getName())
                .productPrice(product.getPrice())
                .productImageUrl(product.getImageUrl())
                .message(message)
                .build();
    }

}
