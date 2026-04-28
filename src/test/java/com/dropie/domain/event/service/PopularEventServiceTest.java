package com.dropie.domain.event.service;

import com.dropie.domain.event.dto.response.PopularEventResponse;
import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Redis 사용, local에서 Redis 띄워져 있어야 통과
// CI에선 Testcontainers로 띄우거나 @SpringBootTest 자체를 분리해야 함 (TODO)
@SpringBootTest
class PopularEventServiceTest {

    @Autowired
    private PopularEventService popularEventService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        // 키 충돌 방지를 위해 popularity:* 전부 정리
        var keys = redisTemplate.keys("popularity:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @AfterEach
    void tearDown() {
        var keys = redisTemplate.keys("popularity:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("점수 누적 — VIEW와 ORDER가 합산되어 ZSET에 반영된다")
    void 점수_누적_성공() {
        // given
        Event event = eventRepository.save(Event.builder()
                .brandName("테스트")
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusHours(1))
                .status(EventStatus.OPEN)
                .build());

        // when — 조회 3번 + 주문 1번 = 1*3 + 5 = 8점
        popularEventService.addScore(event.getId(), PopularEventService.VIEW_SCORE);
        popularEventService.addScore(event.getId(), PopularEventService.VIEW_SCORE);
        popularEventService.addScore(event.getId(), PopularEventService.VIEW_SCORE);
        popularEventService.addScore(event.getId(), PopularEventService.ORDER_SCORE);

        //then
        List<PopularEventResponse> top = popularEventService.getTop10();
        assertThat(top).hasSize(1);
        assertThat(top.get(0).getId()).isEqualTo(event.getId());
        assertThat(top.get(0).getScore()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("TOP10 — 점수 높은 순으로 정렬되며 최대 10개까지만 반환")
    void TOP10_정렬_및_상한() {
        // given — 12개 이벤트 생성, 각각 다른 점수 부여
        for (int i = 1; i <= 12; i++) {
            Event ev = eventRepository.save(Event.builder()
                    .brandName("E" + i)
                    .startAt(LocalDateTime.now())
                    .endAt(LocalDateTime.now().plusHours(1))
                    .status(EventStatus.OPEN)
                    .build());
            popularEventService.addScore(ev.getId(), i); // 1점부터 12점까지
        }

        // when
        List<PopularEventResponse> top = popularEventService.getTop10();

        // then — 12점부터 3점까지 10개, 내림차순
        assertThat(top).hasSize(10);
        assertThat(top.get(0).getScore()).isEqualTo(12.0);
        assertThat(top.get(9).getScore()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("이벤트가 하나도 없으면 빈 리스트 반환")
    void TOP10_빈리스트() {
        List<PopularEventResponse> top = popularEventService.getTop10();
        assertThat(top).isEmpty();
    }

    @Test
    @DisplayName("DB에서 삭제된 이벤트는 TOP10에서 제외 — Redis와 DB 정합성")
    void 삭제된_이벤트는_제외() {
        // given
        Event ev = eventRepository.save(Event.builder()
                .brandName("곧삭제")
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusHours(1))
                .status(EventStatus.OPEN)
                .build());
        popularEventService.addScore(ev.getId(), 10);

        // 이벤트 삭제
        eventRepository.delete(ev);
        eventRepository.flush();

        // when
        List<PopularEventResponse> top = popularEventService.getTop10();

        // then — 빈 리스트 (DB에 없으니 응답에서 제외)
        assertThat(top).isEmpty();
    }
}