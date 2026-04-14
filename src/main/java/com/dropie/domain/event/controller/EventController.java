package com.dropie.domain.event.controller;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.service.EventService;
import com.dropie.global.common.PageResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // GET /events?page=1&size=6
    // 쿼리 파라미터 없으면 defaultValue로 기본값 적용
    // 인증 없이 접근 가능 (메인 페이지는 비로그인도 볼 수 있어야 함)
    @GetMapping
    public ResponseEntity<PageResponse<EventListResponse>> getEvents(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "6") @Min(1) int size
    ) {
        log.debug("[GET /events] page={}, size={}", page, size);
        return ResponseEntity.ok(eventService.getEvents(page, size));
    }

    // GET /events/{eventId}?page=1&size=5
    @GetMapping("/{eventId}")
    public ResponseEntity<EventDetailResponse> getEventDetail(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "5") @Min(1) int size
    ) {
        log.debug("[GET /events/{}] page={}, size={}", eventId, page, size);
        return ResponseEntity.ok(eventService.getEventDetail(eventId, page, size));
    }
}
