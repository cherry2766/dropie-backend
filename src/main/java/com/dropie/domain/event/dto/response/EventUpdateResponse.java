package com.dropie.domain.event.dto.response;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import lombok.Builder;
import lombok.Getter;

// PATCH 200 응답 — { id, brandName, status }
// EventCreateResponse와 필드가 같지만 의미상 분리
@Getter
@Builder
public class EventUpdateResponse {

    private Long id;
    private String brandName;
    private EventStatus status;

    public static EventUpdateResponse from(Event event) {
        return EventUpdateResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .status(event.getStatus())
                .build();
    }
}
