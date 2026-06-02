package com.dropie.domain.event.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// PATCH 요청 DTO — 변경할 필드만 포함, 모든 필드 null 허용
// @Valid 없음 — null이면 기존값 유지, brandName은 변경 불가라 포함하지 않음
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventRequest {

    private String description;
    private String thumbnailImageUrl;
    private String imageUrl;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
