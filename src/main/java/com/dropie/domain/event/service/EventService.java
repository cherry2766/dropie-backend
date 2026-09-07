package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.dto.response.LineupRoundResponse;
import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.policy.EventStatusCalculator;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ProductRepository productRepository;
    private final PopularEventService popularEventService;

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
        LocalDateTime now = LocalDateTime.now();
        Page<Event> events = (status != null)
                ? eventRepository.findByStatus(status, pageable)
                : eventRepository.findAll(pageable);

        // 조회된 이벤트들의 "재고 있음 여부"를 한 번의 쿼리로 가져옴 (N+1 방지)
        // 이벤트마다 재고 조회를 하면 목록 1번 + 이벤트 수만큼 N번 = 1+N 쿼리가 나감
        Set<Long> inStockEventIds = findInStockEventIds(events.getContent());

        Page<EventListResponse> result = events.map(event ->
                // 위 목록에 없으면 재고가 남은 상품이 하나도 없다는 뜻 → 품절
                EventListResponse.from(event, now, !inStockEventIds.contains(event.getId()))
        );

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

        LocalDateTime now = LocalDateTime.now();
        boolean allSoldOut = !productRepository.existsByEventAndStockGreaterThan(event, 0);

        // 인기 점수 누적
        try {
            popularEventService.addScore(eventId, PopularEventService.VIEW_SCORE);
        } catch (Exception e) {
            log.warn("[getEventDetail] 인기 점수 누적 실패 - eventId={}", eventId, e);
        }

        return EventDetailResponse.of(event, products, now, allSoldOut);
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

        // 차수 상태는 그룹의 첫 이벤트 기준으로 계산하므로, 대표 이벤트들만 모아
        // 재고 여부를 한 번에 조회한다 (차수마다 조회하면 N+1)
        List<Event> firstEvents = grouped.values().stream().map(group -> group.get(0)).toList();
        Set<Long> inStockEventIds = findInStockEventIds(firstEvents);

        // 1차부터 순서대로 차수 번호 부여
        List<LineupRoundResponse> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int round = 1;
        for (List<Event> group : grouped.values()) {
            Event first = group.get(0);
            boolean allSoldOut = !inStockEventIds.contains(first.getId());
            result.add(LineupRoundResponse.builder()
                    .round(round++)
                    .status(EventStatusCalculator.resolve(first, now, allSoldOut).name())
                    .brands(group.stream().map(Event::getBrandName).toList())
                    .build());
        }
        return result;
    }

    // 이벤트 목록을 받아 "재고가 남은 상품이 하나라도 있는" 이벤트 id를 한 번의 쿼리로 조회
    // 반환값을 Set으로 만드는 이유: 이후 contains() 판정이 이벤트 수와 무관하게 빠름
    private Set<Long> findInStockEventIds(List<Event> events) {
        // 조회할 이벤트가 없으면 쿼리 자체를 보내지 않음
        // (빈 리스트를 IN 절에 넣으면 DB/드라이버에 따라 문법 오류가 날 수 있음)
        if (events.isEmpty()) {
            return Set.of();
        }

        List<Long> eventIds = events.stream().map(Event::getId).toList();
        return new HashSet<>(productRepository.findEventIdsHavingStock(eventIds));
    }
}
