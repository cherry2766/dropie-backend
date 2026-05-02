package com.dropie.domain.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

// 사용자별 취향 태그 점수 관리
//
// Redis Key: user:taste:{userId}
// 자료구조:  ZSET (member=tagId, score=누적점수)
// TTL:       90일 (활동이 끊긴 사용자 자동 정리)
//
// 점수 정책:
//   - 회원가입 태그 시드: +0.5점 (의도 신호)
//   - 주문 1건당 태그   : +1.0점 (행동 신호 — 더 강함)
@Slf4j
@Service
@RequiredArgsConstructor
public class TasteTagService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "user:taste:";
    private static final Duration TTL = Duration.ofDays(90);
    private static final double SCORE_PER_ORDER = 1.0;
    private static final double SCORE_PER_SEED = 0.5;

    // 주문 완료 시 호출 — 강한 행동 신호
    public void addTagScores(Long userId, List<Long> tagIds) {
        addScores(userId, tagIds, SCORE_PER_ORDER, "order");
    }

    // 회원가입 태그 등록 시 호출 — 약한 의도 신호
    // 사용자가 회원가입 태그를 안 고르면 빈 리스트가 와서 아무 동작 안 함
    public void addSeedScores(Long userId, List<Long> tagIds) {
        addScores(userId, tagIds, SCORE_PER_SEED, "seed");
    }

    private void addScores(Long userId, List<Long> tagIds, double score, String reason) {
        if (tagIds == null || tagIds.isEmpty()) return;

        String key = key(userId);
        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();

        for (Long tagId : tagIds) {
            zset.incrementScore(key, tagId.toString(), score);
        }
        redisTemplate.expire(key, TTL);

        log.debug("[TasteTag] +{}점 x {}태그 → userId={} ({})", score, tagIds.size(), userId, reason);
    }

    // 상위 N개 태그 ID — 점수 내림차순. 비어있으면 빈 리스트 (호출부가 인기 폴백 결정)
    public List<Long> getTopTagIds(Long userId, int limit) {
        Set<String> top = redisTemplate.opsForZSet()
                .reverseRange(key(userId), 0, limit - 1L);

        if (top == null || top.isEmpty()) return List.of();
        return top.stream().map(Long::parseLong).toList();
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

}
