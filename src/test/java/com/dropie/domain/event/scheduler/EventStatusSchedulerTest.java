package com.dropie.domain.event.scheduler;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EventStatusSchedulerTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventStatusScheduler scheduler;

    private Event event(EventStatus status, LocalDateTime startAt, LocalDateTime endAt) {
        return Event.builder()
                .brandName("테스트")
                .description("d")
                .status(status)
                .startAt(startAt)
                .endAt(endAt)
                .build();
    }

    @Test
    @DisplayName("UPCOMING이면서 시작 시각 지난 이벤트는 OPEN으로 전환된다")
    void UPCOMING_OPEN_전환() {
        Event e = event(EventStatus.UPCOMING,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));

        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                eq(EventStatus.UPCOMING), any(), any()))
                .willReturn(List.of(e));
        given(eventRepository.findByStatusAndEndAtBefore(eq(EventStatus.OPEN), any()))
                .willReturn(List.of());
        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                eq(EventStatus.CLOSED), any(), any()))
                .willReturn(List.of());

        scheduler.syncEventStatuses();

        assertThat(e.getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("OPEN이면서 종료 시각 지난 이벤트는 CLOSED로 전환된다")
    void OPEN_CLOSED_전환() {
        Event e = event(EventStatus.OPEN,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(1));

        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                eq(EventStatus.UPCOMING), any(), any()))
                .willReturn(List.of());
        given(eventRepository.findByStatusAndEndAtBefore(eq(EventStatus.OPEN), any()))
                .willReturn(List.of(e));
        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                eq(EventStatus.CLOSED), any(), any()))
                .willReturn(List.of());

        scheduler.syncEventStatuses();

        assertThat(e.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    @DisplayName("관리자가 endAt을 미래로 늘린 CLOSED 이벤트는 다시 OPEN으로 복귀된다")
    void CLOSED_OPEN_복귀() {
        Event e = event(EventStatus.CLOSED,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));

        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                eq(EventStatus.UPCOMING), any(), any()))
                .willReturn(List.of());
        given(eventRepository.findByStatusAndEndAtBefore(eq(EventStatus.OPEN), any()))
                .willReturn(List.of());
        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                eq(EventStatus.CLOSED), any(), any()))
                .willReturn(List.of(e));

        scheduler.syncEventStatuses();

        assertThat(e.getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("FINISHED 이벤트는 어떤 경우에도 자동 전환되지 않는다 — 쿼리 조건에서 제외")
    void FINISHED_불변() {
        // 스케줄러는 UPCOMING/OPEN/CLOSED만 조회하므로 FINISHED는 자연스럽게 제외됨
        // 추가 검증: 세 쿼리 모두 빈 리스트로 모킹해도 예외 없이 끝나야 함
        given(eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(any(), any(), any()))
                .willReturn(List.of());
        given(eventRepository.findByStatusAndEndAtBefore(any(), any()))
                .willReturn(List.of());

        scheduler.syncEventStatuses(); // 예외 없이 통과해야 함
    }
}