package com.dropie.domain.event.controller;

import com.dropie.domain.event.dto.response.PopularEventResponse;
import com.dropie.domain.event.service.PopularEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "인기 이벤트", description = "Redis ZSET 기반 실시간 TOP 10 (최근 7일)")
@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class PopularEventController {

    private final PopularEventService popularEventService;

    @Operation(
            summary = "인기 이벤트 TOP 10",
            description = """
            Redis ZSET 기반 인기 순위 (최근 7일). 조회 시 점수 +1.0, 주문 완료 시 +5.0.
            콜드 스타트 폴백으로 사용 가능.
            """
    )
    // GET /events/popular — 최근 7일 기준 인기 이벤트 TOP10
    @GetMapping("/popular")
    public ResponseEntity<List<PopularEventResponse>> getPopular() {
        return ResponseEntity.ok(popularEventService.getTop10());
    }
}
