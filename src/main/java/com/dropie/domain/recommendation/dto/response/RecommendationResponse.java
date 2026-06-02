package com.dropie.domain.recommendation.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "AI 개인화 추천 응답")
@Getter
@Builder
public class RecommendationResponse {
    @Schema(description = "이벤트 ID", example = "42")
    private Long eventId;

    @Schema(description = "브랜드명", example = "돌담제과")
    private String brandName;

    @Schema(description = "이벤트 썸네일 이미지 URL", example = "https://dropie-bucket.s3.ap-northeast-2.amazonaws.com/events/42/thumb.jpg")
    private String thumbnailImageUrl;

    @Schema(description = "드롭 오픈 시각", example = "2026-05-10T18:00:00")
    private LocalDateTime startAt;

    @Schema(description = "드롭 종료 시각", example = "2026-05-10T20:00:00")
    private LocalDateTime endAt;

    @Schema(description = "대표 상품 ID", example = "108")
    private Long productId;

    @Schema(description = "상품명", example = "말차 생크림 롤")
    private String productName;

    @Schema(description = "상품 가격(원)", example = "8500")
    private int productPrice;

    @Schema(description = "상품 이미지 URL", example = "https://dropie-bucket.s3.ap-northeast-2.amazonaws.com/products/108/main.jpg")
    private String productImageUrl;

    @Schema(description = "Claude가 생성한 개인화 추천 문구", example = "한입에 무너지는 말차 결. 오늘만의 한정 드롭, 추천해요.")
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
