package com.dropie.domain.event.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PopularEventResponse {
    private Long id;
    private String brandName;
    private String thumbnailImageUrl;
    private EventStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    @JsonIgnore
    private double score; // 누적 점수 (디버깅/정렬 검증용, 응답엔 노출 안 함)

    public static PopularEventResponse of(Event event, double score) {
        return PopularEventResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .thumbnailImageUrl(event.getThumbnailImageUrl())
                .status(event.getStatus())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .score(score)
                .build();
    }
}
