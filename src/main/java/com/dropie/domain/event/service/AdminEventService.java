package com.dropie.domain.event.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.dto.request.CreateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventStatusRequest;
import com.dropie.domain.event.dto.response.EventCreateResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.dto.response.EventStatusResponse;
import com.dropie.domain.event.dto.response.EventUpdateResponse;
import com.dropie.global.exception.custom.EventNotFoundException;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;
    private final S3Service s3Service;

    // 이벤트 전체 목록 조회 — 관리자 페이지에서 등록한 이벤트 목록을 보여줄 때 사용
    // readOnly = true: 데이터 변경 없으므로 조회 전용 트랜잭션으로 성능 최적화
    @Transactional(readOnly = true)
    public List<EventListResponse> getEvents() {
        log.debug("[getEvents] 이벤트 전체 목록 조회");
        return eventRepository.findAll().stream()
                .map(EventListResponse::from)
                .toList();
    }

    // 이벤트 등록
    // status는 항상 UPCOMING으로 고정 — 등록 직후 OPEN이 되면 안 되므로 요청값 무시
    @Transactional
    public EventCreateResponse createEvent(CreateEventRequest request) {
        log.debug("[createEvent] 등록 요청 - brandName: {}", request.getBrandName());

        Event event = Event.builder()
                .brandName(request.getBrandName())
                .description(request.getDescription())
                .thumbnailImageUrl(request.getThumbnailImageUrl())
                .imageUrl(request.getImageUrl())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(EventStatus.UPCOMING) // 등록 시 무조건 UPCOMING
                .build();

        EventCreateResponse response = EventCreateResponse.from(eventRepository.save(event));
        log.info("[createEvent] 등록 완료 - eventId: {}", response.getId());
        return response;
    }

    // 이벤트 수정
    // @Transactional → dirty checking으로 save() 없이 변경사항 자동 반영
    @Transactional
    public EventUpdateResponse updateEvent(Long eventId, UpdateEventRequest request) {
        log.debug("[updateEvent] 수정 요청 - eventId: {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(()-> {
                    log.warn("[updateEvent] 이벤트 없음 - eventId: {}", eventId);
                    return new EventNotFoundException();
                });

        event.update(
                request.getDescription(),
                request.getThumbnailImageUrl(),
                request.getImageUrl(),
                request.getStartAt(),
                request.getEndAt()
        );

        log.info("[updateEvent] 수정 완료 - eventId: {}", eventId);
        return EventUpdateResponse.from(event);
    }

    // 이벤트 상태 변경
    // 허용되지 않는 전환이면 Event.changeStatus()에서 INVALID_STATUS_TRANSITION 예외 발생
    @Transactional
    public EventStatusResponse changeEventStatus(Long eventId, UpdateEventStatusRequest request) {
        log.debug("[changeEventStatus] 상태 변경 요청 - eventId: {}, status: {}", eventId, request.getStatus());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("[changeEventStatus] 이벤트 없음 - eventId: {}", eventId);
                    return new EventNotFoundException();
                });

        event.changeStatus(request.getStatus()); // 허용 불가 전환 시 예외

        log.info("[changeEventStatus] 상태 변경 완료 - eventId: {}, newStatus: {}", eventId, request.getStatus());
        return EventStatusResponse.from(event);
    }

    // 이벤트 삭제
    @Transactional
    public void deleteEvent(Long eventId) {
        log.debug("[deleteEvent] 삭제 요청 - eventId: {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("[deleteEvent] 이벤트 없음 - eventId: {}", eventId);
                    return new EventNotFoundException();
                });

        // 이벤트에 속한 모든 상품 이미지를 S3에서 먼저 삭제
        // DB 삭제 전에 해야 imageUrl을 꺼낼 수 있음
        event.getProducts().forEach(product ->
                s3Service.deleteImage(product.getImageUrl()));

        // 이벤트 이미지(썸네일 + 상세 이미지) S3에서 삭제
        s3Service.deleteImage(event.getThumbnailImageUrl());
        s3Service.deleteImage(event.getImageUrl());

        // DB에서 이벤트 삭제 (cascade로 하위 상품도 자동 삭제)
        eventRepository.delete(event);
        log.info("[deleteEvent] 삭제 완료 - eventId: {}", eventId);
    }
}
