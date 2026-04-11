package com.dropie.domain.event.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.dto.request.CreateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventRequest;
import com.dropie.domain.event.dto.request.UpdateEventStatusRequest;
import com.dropie.domain.event.dto.response.EventCreateResponse;
import com.dropie.domain.event.dto.response.EventStatusResponse;
import com.dropie.domain.event.dto.response.EventUpdateResponse;
import com.dropie.global.exception.custom.EventNotFoundException;
import com.dropie.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;

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

        eventRepository.delete(event);
        log.info("[deleteEvent] 삭제 완료 - eventId: {}", eventId);
    }
}
