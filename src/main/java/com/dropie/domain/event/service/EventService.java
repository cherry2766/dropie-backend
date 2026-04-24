package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.dto.response.LineupRoundResponse;
import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.product.dto.response.ProductResponse;
import com.dropie.domain.product.repository.ProductRepository;
import com.dropie.global.common.PageResponse;
import com.dropie.global.exception.custom.EventNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ProductRepository productRepository;

    // GET /events — 이벤트 목록 조회
    // status가 null이면 전체 조회, 값이 있으면 해당 상태만 필터링
    // page는 API 스펙상 1-based이므로 PageRequest 생성 시 -1 해서 0-based로 변환
    // 최신 이벤트가 먼저 보이도록 id 내림차순 정렬
    @Transactional(readOnly = true)
    public PageResponse<EventListResponse> getEvents(int page, int size, EventStatus status) {
        log.debug("[getEvents] page={}, size={}, status={}", page, size, status);

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));

        // status 값 유무에 따라 다른 쿼리 실행
        // null이면 전체 조회(기존 동작 유지), 아니면 상태 필터 조회
        Page<EventListResponse> result = (status != null)
                ? eventRepository.findByStatus(status, pageable).map(EventListResponse::from)
                : eventRepository.findAll(pageable).map(EventListResponse::from);

        return PageResponse.from(result);
    }

    // GET /events/{eventId} — 이벤트 상세 조회
    // 이벤트 조회 + 해당 이벤트의 상품 목록 조회
    // 이벤트가 없으면 404 반환
    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(Long eventId, int page, int size) {
        log.debug("[getEventDetail] eventId={}, page={}, size={}", eventId, page, size);

        // 이벤트 조회 — 없으면 404
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        // 해당 이벤트의 상품 목록 페이지네이션 조회
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "id"));

        Page<ProductResponse> productPage = productRepository.findByEvent(event, pageable)
                .map(ProductResponse::from);

        PageResponse<ProductResponse> products = PageResponse.from(productPage);

        return EventDetailResponse.of(event, products);
    }

    // GET /events/lineup — 라인업 조회
    // startAt + endAt이 같은 이벤트를 같은 차수로 묶어서 반환
    @Transactional(readOnly = true)
    public List<LineupRoundResponse> getLineup() {
        List<Event> events = eventRepository.findAllByOrderByStartAtAsc();

        // LinkedHashMap: 삽입 순서 유지 → startAt 정렬 순서가 그대로 차수 순서가 됨
        Map<String, List<Event>> grouped = new LinkedHashMap<>();
        for (Event event : events) {
            String key = event.getStartAt() + "_" + event.getEndAt();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }

        // 1차부터 순서대로 차수 번호 부여
        List<LineupRoundResponse> result = new ArrayList<>();
        int round = 1;
        for (List<Event> group : grouped.values()) {
            result.add(LineupRoundResponse.builder()
                    .round(round++)
                    .status(group.get(0).getStatus().name())
                    .brands(group.stream().map(Event::getBrandName).toList())
                    .build());
        }
        return result;
    }
}
