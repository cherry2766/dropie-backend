package com.dropie.domain.event.policy;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class EventStatusCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 25, 20, 0);

    private Event eventOf(EventStatus status, LocalDateTime startAt, LocalDateTime endAt) {
        return Event.builder()
                .brandName("테스트브랜드")
                .description("desc")
                .startAt(startAt)
                .endAt(endAt)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("FINISHED는 시간/재고와 무관하게 FINISHED로 노출된다")
    void FINISHED_최우선() {
        Event e = eventOf(EventStatus.FINISHED, NOW.minusHours(1), NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.FINISHED);
    }

    @Test
    @DisplayName("CLOSED는 시간이 판매 중이어도 CLOSED로 노출된다 — 관리자 일시 중단 의도 존중")
    void CLOSED_관리자_의도_존중() {
        Event e = eventOf(EventStatus.CLOSED, NOW.minusHours(1), NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    @DisplayName("전 상품 품절이면 SOLD_OUT — 시간보다 우선")
    void 전_상품_품절_SOLD_OUT() {
        Event e = eventOf(EventStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, true)).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("종료 시각을 지난 이벤트는 CLOSED")
    void 종료_시각_경과_CLOSED() {
        Event e = eventOf(EventStatus.OPEN, NOW.minusHours(2), NOW.minusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    @DisplayName("판매 시간 내이면 OPEN")
    void 판매_시간_내_OPEN() {
        Event e = eventOf(EventStatus.UPCOMING, NOW.minusMinutes(1), NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("시작 시각 직전이면 UPCOMING")
    void 시작_시각_이전_UPCOMING() {
        Event e = eventOf(EventStatus.UPCOMING, NOW.plusMinutes(1), NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.UPCOMING);
    }

    @Test
    @DisplayName("startAt 정각(==now)이면 OPEN — 경계값")
    void startAt_경계값_OPEN() {
        Event e = eventOf(EventStatus.UPCOMING, NOW, NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("endAt 정각(==now)이면 OPEN — 경계값 (now > endAt 일 때만 CLOSED)")
    void endAt_경계값_OPEN() {
        Event e = eventOf(EventStatus.UPCOMING, NOW.minusHours(1), NOW);
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("DB가 UPCOMING이지만 시간이 이미 판매 중이면 화면은 OPEN — 스케줄러 지연을 메꾸는 케이스")
    void 스케줄러_지연_보정() {
        Event e = eventOf(EventStatus.UPCOMING, NOW.minusSeconds(30), NOW.plusHours(1));
        assertThat(EventStatusCalculator.resolve(e, NOW, false)).isEqualTo(EventStatus.OPEN);
    }
}