package com.dropie.domain.event.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.policy.EventStatusCalculator;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// GET /admin/events 전용 응답 DTO
// EventListResponse(사용자용)와 달리 관리자가 필요한 모든 필드를 포함
@Getter
@Builder
public class AdminEventResponse {

    private Long id;
    private String brandName;
    private String description;
    private String thumbnailImageUrl;
    private String imageUrl;
    private EventStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public static AdminEventResponse from(Event event, LocalDateTime now, boolean allSoldOut) {
        return AdminEventResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .description(event.getDescription())
                .thumbnailImageUrl(event.getThumbnailImageUrl())
                .imageUrl(event.getImageUrl())
                .status(EventStatusCalculator.resolve(event, now, allSoldOut))
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .build();
    }
}
