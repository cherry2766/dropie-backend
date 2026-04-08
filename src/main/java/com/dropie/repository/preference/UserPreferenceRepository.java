package com.dropie.repository.preference;

import com.dropie.domain.preference.UserPreference;
import com.dropie.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    // 온보딩 재시도 시 기존 취향 태그 전체 삭제 후 새로 저장하기 위한 메서드
    // → 중복 등록 방지 + 덮어쓰기 가능하게
    void deleteByUser(User user);
}
