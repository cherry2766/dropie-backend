package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.EventDetailResponse;
import com.dropie.domain.event.dto.response.EventListResponse;
import com.dropie.domain.event.entity.Event;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ProductRepository productRepository;

    // GET /events — 이벤트 목록 조회
    // page는 API 스펙상 1-based이므로 PageRequest 생성 시 -1 해서 0-based로 변환
    // 최신 이벤트가 먼저 보이도록 id 내림차순 정렬
    @Transactional(readOnly = true)
    public PageResponse<EventListResponse> getEvents(int page, int size) {
        log.debug("[getEvents] page={}, size={}", page, size);

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<EventListResponse> result = eventRepository.findAll(pageable)
                .map((EventListResponse::from)); // Event → EventListResponse 변환

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
}
