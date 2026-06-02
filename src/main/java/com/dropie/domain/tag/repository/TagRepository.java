package com.dropie.domain.tag.repository;

import com.dropie.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    // 회원가입용 GET /tags — onboardingExposed=true만
    List<Tag> findAllByOnboardingExposedTrue();

    // ProductTag 시드 점수 / 추천 시 태그명 조회용 — N개 ID로 한 번에 조회
    List<Tag> findAllByIdIn(List<Long> ids);

    // find-or-create — 상품 등록 시 이름으로 태그 찾기
    Optional<Tag> findByName(String name);

    // 어드민 자동완성 — 부분 일치 검색
    List<Tag> findTop20ByNameContaining(String keyword);
}
