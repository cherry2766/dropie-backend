package com.dropie.domain.tag.repository;

import com.dropie.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    // tagIds 목록으로 Tag 엔티티 한 번에 조회
    // → N+1 방지 (tagId 하나씩 findById 하지 않기 위해)
    List<Tag> findAllByIdIn(List<Long> tagIds);
}
