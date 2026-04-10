package com.dropie.dto.response.event;

import com.dropie.domain.enums.EventStatus;
import com.dropie.domain.event.Event;
import lombok.Builder;
import lombok.Getter;

// POST 201 응답 — { id, brandName, status }
@Getter
@Builder
public class EventCreateResponse {

    private Long id;
    private String brandName;
    private EventStatus status;

    public static EventCreateResponse from(Event event) {
        return EventCreateResponse.builder()
                .id(event.getId())
                .brandName(event.getBrandName())
                .status(event.getStatus())
                .build();
    }
}
