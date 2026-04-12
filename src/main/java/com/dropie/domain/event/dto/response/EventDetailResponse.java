package com.dropie.domain.event.dto.response;


import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.product.dto.response.ProductResponse;
import com.dropie.global.common.PageResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// GET /events/{eventId} 상세 응답 DTO
// 목록과 달리 description, imageUrl 포함
// products는 페이지네이션된 별도 조회 결과를 받아서 조합
@Getter
@Builder
public class EventDetailResponse {

    private Long id;
    private String brandName;
    private String description;
    private String imageUrl;
    private EventStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private PageResponse<ProductResponse> products; // 상품 목록 (페이지네이션)

    // Event 엔티티 + 상품 페이지 응답을 조합해서 DTO 생성
    public static EventDetailResponse of(Event event, PageResponse<ProductResponse> products) {
        return EventDetailResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .description(event.getDescription())
                .imageUrl(event.getImageUrl())
                .status(event.getStatus())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .products(products)
                .build();
    }
}
