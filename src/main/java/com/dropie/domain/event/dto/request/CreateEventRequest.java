package com.dropie.domain.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// POST 요청 DTO — 필수값은 @NotBlank / @NotNull로 검증
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {

    @NotBlank
    private String brandName;

    @NotBlank
    private String description;

    private String thumbnailImageUrl;

    private String imageUrl;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;
}
