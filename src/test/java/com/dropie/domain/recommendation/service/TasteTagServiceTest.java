package com.dropie.domain.recommendation.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TasteTagServiceTest {

    @Autowired
    private TasteTagService tasteTagService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long USER_ID = 1L;

    @BeforeEach
    @AfterEach
    void clean() {
        var keys = redisTemplate.keys("user:taste:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("주문 누적 — 같은 태그가 여러 번 들어오면 그만큼 누적")
    void 주문_누적() {
        tasteTagService.addTagScores(USER_ID, List.of(10L, 20L, 10L));

        List<Long> top = tasteTagService.getTopTagIds(USER_ID, 5);
        assertThat(top).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("회원가입 시드 — +0.5점씩 누적 (주문보다 약함)")
    void 회원가입_시드() {
        tasteTagService.addSeedScores(USER_ID, List.of(10L, 20L));  // 둘 다 +0.5
        tasteTagService.addTagScores(USER_ID, List.of(20L));    // 20번에 +1 추가 → 1.5

        List<Long> top = tasteTagService.getTopTagIds(USER_ID, 5);
        assertThat(top).containsExactly(20L, 10L);  // 1.5 > 0.5
    }

    @Test
    @DisplayName("빈 리스트 입력 — 아무것도 누적되지 않음 (회원가입 건너뛴 경우)")
    void 빈_리스트_무동작() {
        tasteTagService.addSeedScores(USER_ID, List.of());
        assertThat(tasteTagService.getTopTagIds(USER_ID,5)).isEmpty();
    }

    @Test
    @DisplayName("사용자 데이터 없음 — 빈 리스트 반환 → 호출부가 인기 폴백 결정")
    void 사용자_없음_빈리스트() {
        assertThat(tasteTagService.getTopTagIds(999L, 5)).isEmpty();
    }

}