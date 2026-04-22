package com.dropie.domain.event.repository;

import com.dropie.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // startAt 오름차순 전체 조회
    List<Event> findAllByOrderByStartAtAsc();
}
