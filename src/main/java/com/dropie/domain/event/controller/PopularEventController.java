package com.dropie.domain.event.controller;

import com.dropie.domain.event.dto.response.PopularEventResponse;
import com.dropie.domain.event.service.PopularEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class PopularEventController {

    private final PopularEventService popularEventService;

    // GET /events/popular — 최근 7일 기준 인기 이벤트 TOP10
    @GetMapping("/popular")
    public ResponseEntity<List<PopularEventResponse>> getPopular() {
        return ResponseEntity.ok(popularEventService.getTop10());
    }
}
