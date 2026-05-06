package com.dropie.domain.event.controller;

import com.dropie.domain.event.dto.request.CreateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventStatusRequest;
import com.dropie.domain.event.dto.response.EventCreateResponse;
import com.dropie.domain.event.dto.response.AdminEventResponse;
import com.dropie.domain.event.dto.response.EventStatusResponse;
import com.dropie.domain.event.dto.response.EventUpdateResponse;
import com.dropie.domain.event.service.AdminEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 이벤트", description = "이벤트 등록·수정·상태 변경·삭제")
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService adminEventService;

    // 이벤트 전체 목록 조회
    // GET /admin/events → 200
    @GetMapping("/events")
    public ResponseEntity<List<AdminEventResponse>> getEvents() {
        log.debug("[GET /admin/events]");
        return ResponseEntity.ok(adminEventService.getEvents());
    }

    // 이벤트 등록
    // POST /admin/events → 201
    @PostMapping("/events")
    public ResponseEntity<EventCreateResponse> createEvent(
            @RequestBody @Valid CreateEventRequest request) {
        log.debug("[POST /admin/events] brandName: {}", request.getBrandName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminEventService.createEvent(request));
    }

    // 이벤트 수정
    // PATCH /admin/events/{eventId} → 200
    // PATCH는 변경할 필드만 포함 → @Valid 없음
    @PatchMapping("/events/{eventId}")
    public ResponseEntity<EventUpdateResponse> updateEvent(
            @PathVariable Long eventId,
            @RequestBody UpdateEventRequest request) {
        log.debug("[PATCH /admin/events/{}]", eventId);
        return ResponseEntity.ok(adminEventService.updateEvent(eventId, request));
    }

    // 이벤트 상태 변경
    // PATCH /admin/events/{eventId}/status → 200
    // 허용되지 않는 전환이면 400 INVALID_STATUS_TRANSITION (GlobalExceptionHandler에서 처리)
    @PatchMapping("/events/{eventId}/status")
    public ResponseEntity<EventStatusResponse> changeEventStatus(
            @PathVariable Long eventId,
            @RequestBody @Valid UpdateEventStatusRequest request) {
        log.debug("[PATCH /admin/events/{}/status] status: {}", eventId, request.getStatus());
        return ResponseEntity.ok(adminEventService.changeEventStatus(eventId, request));
    }

    // 이벤트 삭제
    // DELETE /admin/events/{eventId} → 204 (응답 바디 없음)
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long eventId) {
        log.debug("[DELETE /admin/events/{}]", eventId);
        adminEventService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }
}
