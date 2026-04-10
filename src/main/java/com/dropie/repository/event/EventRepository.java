package com.dropie.repository.event;

import com.dropie.domain.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
