package com.dropie.domain.event.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.policy.EventStatusCalculator;
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
    // allSoldOut: 호출부에서 ProductRepository를 통해 한 번에 조회한 결과를 주입
    public static EventListResponse from(Event event, LocalDateTime now, boolean allSoldOut) {
        return EventListResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .thumbnailImageUrl(event.getThumbnailImageUrl())
                .status(EventStatusCalculator.resolve(event, now, allSoldOut))
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .build();
    }
}
