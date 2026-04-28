package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.PopularEventResponse;
import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// 인기 이벤트 점수 누적 + TOP10 조회
//
// 데이터 구조:
//   ZSET key = popularity:event:{yyyyMMdd}
//   member   = eventId (문자열)
//   score    = 누적 점수
//
// TOP10 조회 흐름:
//   1) 오늘부터 7일 전까지의 일자별 키를 모음
//   2) ZUNIONSTORE로 합산 (Redis 명령어 한 번에 처리)
//   3) ZREVRANGE로 상위 10개 eventId 추출
//   4) DB에서 IN 쿼리로 이벤트 상세 정보 가져와 응답 조립
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularEventService {

    private final StringRedisTemplate redisTemplate;
    private final EventRepository eventRepository;

    private static final String KEY_PREFIX = "popularity:event:";
    private static final String UNION_KEY = "popularity:event:top:7d";
    private static final int WINDOW_DAYS = 7;
    private static final int TOP_N = 10;
    private static final Duration KEY_TTL = Duration.ofDays(14);
    private static final Duration UNION_TTL = Duration.ofSeconds(60);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 점수 정책
    public static final double VIEW_SCORE = 1.0;
    public static final double ORDER_SCORE = 5.0;

    // 점수 누적

    /**
     * eventId의 점수를 score만큼 증가
     * - 키가 없으면 자동 생성
     * - TTL은 키 생성 직후 한 번만 설정 (이후는 매번 expire 호출 안 함)
     * - Redis 장애 시 예외 발생 → 호출부에서 try-catch로 무시
     */
    public void addScore(Long eventId, double score) {
        String key = todayKey();
        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();

        // ZINCRBY: 멤버가 없으면 0에서 시작해 score만큼 증가
        Double newScore = zset.incrementScore(key, eventId.toString(), score);

        // 키가 새로 만들어진 경우(점수가 정확히 score 또는 점수가 우연히 score와 같아진 경우) TTL 설정
        // (정밀한 판단을 위해선 EXISTS 체크가 더 안전하지만, 매번 호출하면 라운드트립 2배)
        if (newScore != null && newScore == score) {
            redisTemplate.expire(key, KEY_TTL);
        }

        log.debug("[Popularity] +{} -> eventId={}, today={}, total={}",
                score, eventId, key, newScore);
    }

    // TOP10 조회
    public List<PopularEventResponse> getTop10() {
        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();

        // 1) 합산 결과 캐시(union key)가 살아있으면 그대로 사용
        Boolean unionExists = redisTemplate.hasKey(UNION_KEY);

        // 2) 캐시가 없으면 ZUNIONSTORE 실행 → union key에 합산 결과 저장
        if (Boolean.FALSE.equals(unionExists)) {
            List<String> dailyKeys = lastNDaysKeys(WINDOW_DAYS);

            // → 7일치 키를 합쳐서 union key에 저장
            // 일부 키가 없어도 OK (없는 키는 빈 ZSET으로 취급)
            zset.unionAndStore(
                    dailyKeys.get(0),                           // 첫 번째 키
                    dailyKeys.subList(1, dailyKeys.size()),     // 나머지 키들
                    UNION_KEY                                   // 결과 저장 위치
            );
            redisTemplate.expire(UNION_KEY, UNION_TTL);
        }

        // 3) ZREVRANGE WITHSCORES → 점수 높은 순으로 N개
        Set<TypedTuple<String>> top = zset.reverseRangeWithScores(UNION_KEY, 0, TOP_N - 1);
        if (top == null || top.isEmpty()) {
            return List.of();
        }

        // 4) eventId 목록 추출 + DB 조회
        // 순서 유지를 위해 LinkedHashMap 사용
        Map<Long, Double> scoreByEventId = new LinkedHashMap<>();
        for (TypedTuple<String> t : top) {
            if (t.getValue() == null) continue;
            scoreByEventId.put(Long.parseLong(t.getValue()), t.getScore());
        }

        List<Event> events = eventRepository.findAllById(scoreByEventId.keySet());

        // findAllById는 순서를 보장하지 않으므로 점수 순서로 다시 정렬
        Map<Long, Event> eventById = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        return scoreByEventId.entrySet().stream()
                .map(e -> {
                    Event ev = eventById.get(e.getKey());
                    if (ev == null) return null; // DB에서 삭제된 경우
                    return PopularEventResponse.of(ev, e.getValue());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 다른 도메인(AI 추천 등)에서 "단순히 인기 이벤트 ID 목록"이 필요할 때 사용
     */
    public List<Long> getTopEventIds(int limit) {
        return getTop10().stream()
                .limit(limit)
                .map(PopularEventResponse::getId)
                .toList();
    }

    // 헬퍼 메서드

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
    }

    private List<String> lastNDaysKeys(int days) {
        List<String> keys = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            keys.add(KEY_PREFIX + today.minusDays(i).format(DATE_FORMAT));
        }
        return keys;
    }

}
