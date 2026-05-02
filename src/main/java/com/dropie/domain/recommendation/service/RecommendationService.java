package com.dropie.domain.recommendation.service;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import com.dropie.domain.event.service.PopularEventService;
import com.dropie.domain.product.entity.Product;
import com.dropie.domain.recommendation.client.ClaudeApiClient;
import com.dropie.domain.recommendation.dto.response.RecommendationResponse;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

// AI 개인화 추천 핵심 서비스
//
// 동작:
//   1) 캐시 hit → 그대로 반환
//   2) ZSET 상위 태그 조회
//        - 빔 → 인기 폴백
//        - 값 있음 → 태그 매칭 후보 조회
//                                   매칭 후보 빔 → 인기 폴백
//   3) 후보별 Claude 호출 → 추천 문구 생성 (실패 시 기본 문구)
//   4) 결과 60분 캐시
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final TasteTagService tasteTagService;
    private final TagRepository tagRepository;
    private final EventRepository eventRepository;
    private final PopularEventService popularEventService;
    private final ClaudeApiClient claudeApiClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "recommendation:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(60);
    private static final int TOP_TAG_LIMIT = 5;
    private static final int CANDIDATE_LIMIT = 10;
    private static final int RESULT_LIMIT = 3;
    private static final List<EventStatus> RECOMMENDABLE_STATUSES =
            List.of(EventStatus.OPEN, EventStatus.UPCOMING);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("MM월 dd일 HH시");

    @Transactional(readOnly = true)
    public List<RecommendationResponse> getRecommendation(Long userId) {
        // 1) 캐시 hit
        String cached = redisTemplate.opsForValue().get(cacheKey(userId));
        if (cached != null) {
            try {
                return List.of(objectMapper.readValue(cached, RecommendationResponse[].class));
            } catch (Exception e) {
                log.warn("[Recommendation] 캐시 역직렬화 실패 - 무시하고 새로 계산", e);
            }
        }

        // 2) 새로 계산
        List<RecommendationResponse> result = computeRecommendation(userId);

        // 3) 캐시 저장 (실패해도 응답엔 영향 없게)
        try {
            redisTemplate.opsForValue().set(
                    cacheKey(userId),
                    objectMapper.writeValueAsString(result),
                    CACHE_TTL
            );
        } catch (JsonProcessingException e) {
            log.warn("[Recommendation] 캐시 직렬화 실패", e);
        }

        return result;
    }

    private List<RecommendationResponse> computeRecommendation(Long userId) {
        // 사용자 상위 태그 — 시드 + 주문 누적이 합쳐진 결과
        List<Long> topTagIds = tasteTagService.getTopTagIds(userId, TOP_TAG_LIMIT);

        List<Event> candidates;
        List<String> tagNames;

        if (topTagIds.isEmpty()) {
            // 시나리오 1: 회원가입 태그도 없고 주문도 없음 → 인기 폴백
            log.info("[Recommendation] 콜드 스타트 - 인기 폴백 userId={}", userId);
            candidates = popularEventCandidates();
            tagNames = List.of();
        } else {
            // 시나리오 2/3/4: ZSET에 시드 또는 누적 점수가 있음
            tagNames = tagRepository.findAllByIdIn(topTagIds).stream()
                    .map(Tag::getName)
                    .toList();

            candidates = eventRepository.findCandidatesByTagIds(topTagIds, RECOMMENDABLE_STATUSES);
            if (candidates.isEmpty()) {
                // 태그는 있지만 매칭 이벤트가 없음 → 인기 폴백
                log.info("[Recommendation] 태그 매칭 후보 없음 - 인기 폴백 userId={}", userId);
                candidates = popularEventCandidates();
            }
        }

        if (candidates.isEmpty()) return List.of();

        candidates = candidates.stream().limit(RESULT_LIMIT).toList();

        return candidates.stream()
                .map(event -> buildResponse(event, tagNames))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Event> popularEventCandidates() {
        List<Long> popularIds = popularEventService.getTopEventIds(CANDIDATE_LIMIT);
        if (popularIds.isEmpty()) return List.of();

        List<Event> events = eventRepository.findAllById(popularIds);
        return events.stream()
                .filter(e -> RECOMMENDABLE_STATUSES.contains(e.getStatus()))
                .toList();
    }

    private RecommendationResponse buildResponse(Event event, List<String> tagNames) {
        Product representative = event.getProducts().stream().findFirst().orElse(null);
        if (representative == null) return null;

        String message;
        try {
            message = claudeApiClient.generate(buildPrompt(event, representative, tagNames));
        } catch (Exception e) {
            log.warn("[Recommendation] Claude 실패 - 기본 문구 폴백 eventId={}", event.getId());
            message = defaultMessage(event);
        }

        return RecommendationResponse.of(event, representative, message);
    }

    // Claude 프롬프트 — tagNames 비면 "신규 사용자" 톤으로 작성하도록 명시
    private String buildPrompt(Event event, Product product, List<String> tagNames) {
        String tasteTags = tagNames.isEmpty() ? "(없음 — 신규 사용자)" : String.join(", ", tagNames);
        String startAt = event.getStartAt() != null ? event.getStartAt().format(DT_FMT) : "미정";

        return """
                너는 디저트 드롭 플랫폼의 개인화 추천 문구를 작성하는 역할이다.

                [사용자 취향 태그]
                %s

                [추천 이벤트]
                브랜드명: %s
                상품명: %s
                상품 설명: %s
                가격: %d원
                오픈 시간: %s

                [작성 조건]
                - 한국어로 작성
                - 1~3문장
                - 너무 과장하지 말 것
                - 사용자의 취향 태그와 상품 특징을 자연스럽게 연결할 것
                - 구매를 강요하는 표현은 피할 것
                - 포근하고 감성적인 톤
                - 이모지는 사용하지 말 것

                [출력 형식]
                추천 문구만 출력해줘.
                """.formatted(
                tasteTags,
                event.getBrandName(),
                product.getName(),
                product.getDescription() == null ? "-" : product.getDescription(),
                product.getPrice(),
                startAt
        );
    }

    private String defaultMessage(Event event) {
        return event.getBrandName() + "의 새로운 드롭이 곧 시작돼요. 좋아하실 만한 상품이 준비되어 있어요.";
    }

    private String cacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }
}
