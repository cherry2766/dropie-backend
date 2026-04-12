package com.dropie.domain.event.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// GET /events 목록 응답 DTO
@Getter
@Builder
public class EventListResponse {

    private Long id;
    private String brandName;
    private String thumbnailImageUrl;
    private EventStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    // Entity → DTO 변환
    public static EventListResponse from(Event event) {
        return EventListResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .thumbnailImageUrl(event.getThumbnailImageUrl())
                .status(event.getStatus())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .build();
    }
}
