package com.dropie.domain.event.dto.request;

import com.dropie.domain.event.entity.EventStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상태 변경 전용 DTO — status 하나만 받음
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateEventStatusRequest {

    @NotNull
    private EventStatus status;
}
