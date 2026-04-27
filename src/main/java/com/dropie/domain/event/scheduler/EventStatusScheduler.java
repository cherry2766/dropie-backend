package com.dropie.domain.event.scheduler;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import com.dropie.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 시간 기반 자동 상태 전환 — 1분마다 실행
//
// 역할:
//   - UPCOMING 이벤트 중 startAt이 지난 것을 OPEN으로 전환
//   - OPEN 이벤트 중 endAt이 지난 것을 CLOSED로 전환
//   - CLOSED 이벤트 중 endAt이 다시 미래로 이동된 것을 OPEN으로 복귀 (관리자가 endAt 늘린 경우)
//
// 책임 범위:
//   - 시간 기반 전환만 담당. 재고 기반 SOLD_OUT 전환은 OrderService에서 처리
//   - DB의 status 컬럼을 갱신하는 게 목적이지, 사용자 응답을 만드는 게 아님
//     (응답은 EventStatusCalculator가 담당. 스케줄러 지연을 derived가 메꿔줌)
//
// 주기 1분의 의미:
//   - 사용자 응답은 derived가 처리하므로 1분 지연은 화면에 안 보임
//   - DB가 1분 안에 정합 상태로 수렴하는 것만 보장하면 됨
//   - 더 빠르게 할 이유 없음 (DB 부하만 증가)
//
// 테스트 환경에서는 dropie.scheduler.event-status.enabled=false로 빈 등록 자체를 차단
// → 테스트 도중 백그라운드에서 status가 바뀌어 검증이 깨지는 flaky test 방지
// matchIfMissing=true: 운영 yml에 별도 설정 없어도 기본 활성화
@ConditionalOnProperty(
        name = "dropie.scheduler.event-status.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    private final EventRepository eventRepository;

    // cron "0 * * * * *" = 매분 0초에 실행
    // @Transactional: dirty checking으로 changeStatus() 호출만 해도 자동 UPDATE
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void syncEventStatuses() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("[EventStatusScheduler] sync start - now={}", now);

        // 1) UPCOMING → OPEN
        // startAt <= now < endAt 인 UPCOMING 이벤트
        List<Event> toOpen = eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                EventStatus.UPCOMING, now, now);
        for (Event event : toOpen) {
            event.changeStatus(EventStatus.OPEN);
            log.info("[EventStatusScheduler] UPCOMING → OPEN eventId={}", event.getId());
        }

        // 2) OPEN → CLOSED (종료 시각 지남)
        List<Event> toClose = eventRepository.findByStatusAndEndAtBefore(EventStatus.OPEN, now);
        for (Event event : toClose) {
            event.changeStatus(EventStatus.CLOSED);
            log.info("[EventStatusScheduler] OPEN → CLOSED eventId={}", event.getId());
        }

        // 3) CLOSED → OPEN (관리자가 endAt을 미래로 늘린 경우만)
        // 단, FINISHED나 SOLD_OUT은 건드리지 않음
        List<Event> toReopen = eventRepository.findByStatusAndStartAtLessThanEqualAndEndAtAfter(
                EventStatus.CLOSED, now, now);
        for (Event event : toReopen) {
            event.changeStatus(EventStatus.OPEN);
            log.info("[EventStatusScheduler] CLOSED → OPEN eventId={}", event.getId());
        }
    }
}
