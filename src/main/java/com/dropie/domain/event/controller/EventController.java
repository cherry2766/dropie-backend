package com.dropie.domain.event.controller;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.dto.response.LineupRoundResponse;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.service.EventService;
import com.dropie.global.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "이벤트", description = "이벤트 목록·상세 조회 (Derived Status 응답 시점 계산)")
@Slf4j
@Validated
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // GET /events?page=1&size=6&status=OPEN
    // status는 선택값(required = false) — 없으면 전체 조회, 있으면 해당 상태만 필터링
    // EventStatus enum 타입으로 선언하면 Spring이 문자열 "OPEN"을 자동으로 변환해줌
    // 인증 없이 접근 가능 (메인 페이지는 비로그인도 볼 수 있어야 함)
    @GetMapping
    public ResponseEntity<PageResponse<EventListResponse>> getEvents(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "6") @Min(1) int size,
            @RequestParam(required = false) EventStatus status
    ) {
        log.debug("[GET /events] page={}, size={}, status={}", page, size, status);
        return ResponseEntity.ok(eventService.getEvents(page, size, status));
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

    // GET /events/lineup — 라인업 조회
    @GetMapping("/lineup")
    public ResponseEntity<List<LineupRoundResponse>> getLineup() {
        return ResponseEntity.ok(eventService.getLineup());
    }
}
