package com.dropie.domain.event.repository;

import com.dropie.domain.event.entity.Event;
import com.dropie.domain.event.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // startAt 오름차순 전체 조회 (라인업용)
    List<Event> findAllByOrderByStartAtAsc();

    // status가 일치하는 이벤트를 페이지네이션으로 조회
    // Spring Data JPA가 메서드명을 분석해서 "WHERE status = ?" 쿼리를 자동 생성해줌
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    // 스케줄러용: status가 일치하면서 startAt <= now < endAt 인 이벤트
    // UPCOMING → OPEN, CLOSED → OPEN 전환 시 사용
    List<Event> findByStatusAndStartAtLessThanEqualAndEndAtAfter(
            EventStatus status, LocalDateTime startAtBound, LocalDateTime endAtBound
    );

    // 스케줄러용: status가 일치하면서 endAt이 지난 이벤트
    // OPEN → CLOSED 전환 시 사용
    List<Event> findByStatusAndEndAtBefore(EventStatus status, LocalDateTime now);

    // 사용자 상위 태그에 매칭되는 OPEM/UPCOMING 이벤트 후보
    // - 어떤 상품이라도 사용자 태그 중 하나라도 가지면 후보
    // - 같은 이벤트 중복 방지 위해 DISTINCT
    // - 정확한 매칭 강도 정렬은 캐싱/비용 문제로 호출부에서 limit 후 처리
    @Query("""
            SELECT DISTINCT e FROM Event e
            JOIN e.products p
            JOIN p.productTags pt
            WHERE pt.tag.id IN :tagIds
            AND e.status IN :statuses
            ORDER BY e.startAt ASC 
            """)
    List<Event> findCandidatesByTagIds(
            @Param("tagIds") List<Long> tagIds,
            @Param("statuses") List<EventStatus> statuses
    );
}
