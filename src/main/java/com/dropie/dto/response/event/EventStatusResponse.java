package com.dropie.dto.response.event;

import com.dropie.domain.enums.EventStatus;
import com.dropie.domain.event.Event;
import lombok.Builder;
import lombok.Getter;

// 상태 변경 200 응답 — { id, status }
@Getter
@Builder
public class EventStatusResponse {

    private Long id;
    private EventStatus status;

    public static EventStatusResponse from(Event event) {
        return EventStatusResponse.builder()
                .id(event.getId())
                .status(event.getStatus())
                .build();
    }
}
