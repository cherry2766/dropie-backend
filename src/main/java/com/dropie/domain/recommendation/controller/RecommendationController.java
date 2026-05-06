package com.dropie.domain.recommendation.controller;

import com.dropie.domain.recommendation.dto.response.RecommendationResponse;
import com.dropie.domain.recommendation.service.RecommendationService;
import com.dropie.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "추천 (AI)", description = "Redis ZSET + Claude API 기반 개인화 추천 — 4분기 시나리오 단일 통합 + lazy 동기화")
@Slf4j
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(
            summary = "AI 개인화 추천",
            description = """
            사용자 취향 ZSET에서 상위 태그 추출 → 매칭 이벤트 후보 선정 → Claude API로 개인화 문구 생성.
            **처리 흐름**
            1. 캐시 hit 시 즉시 반환 (60분 TTL)
            2. ZSET 비어있으면 DB의 회원가입 태그(+0.5) + PAID 주문 태그(+1.0)를 일회성 시드 — 멱등, 기존 회원·Redis 휘발 자동 복구
            3. 상위 태그로 매칭 이벤트 조회 → Claude API 호출 (실패 시 기본 문구 폴백)
            4. 태그 없거나 매칭 후보 없으면 인기 TOP 폴백
            5. 빈 결과는 캐시하지 않아 데이터 채워지면 즉시 반영
            """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationResponse>> getMyRecommendations(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(recommendationService.getRecommendation(userId));
    }
}
