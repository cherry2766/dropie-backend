package com.dropie.domain.preference.repository;

import com.dropie.domain.preference.entity.UserPreference;
import com.dropie.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    // 해당 유저의 취향 데이터 존재 여부 확인
    // → AuthService에서 showOnboarding 계산에 사용
    boolean existsByUser(User user);

    // lazy 동기화에서 사용 — 회원가입 시점에 등록된 태그 ID 목록 조회
    @Query("SELECT up.tag.id FROM UserPreference up WHERE up.user.id = :userId")
    List<Long> findTagIdsByUserId(@Param("userId") Long userId);
}
