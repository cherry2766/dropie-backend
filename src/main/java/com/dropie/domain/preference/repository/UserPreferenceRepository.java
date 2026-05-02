package com.dropie.domain.preference.repository;

import com.dropie.domain.preference.entity.UserPreference;
import com.dropie.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    // 해당 유저의 취향 데이터 존재 여부 확인
    // → AuthService에서 showOnboarding 계산에 사용
    boolean existsByUser(User user);
}
