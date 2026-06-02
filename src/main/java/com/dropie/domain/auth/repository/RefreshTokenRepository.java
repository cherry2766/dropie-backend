package com.dropie.domain.auth.repository;

import com.dropie.domain.auth.entity.RefreshToken;
import com.dropie.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 유저로 토큰 조회 — 로그인/회원가입 시 기존 토큰이 있는지 확인할 때 사용
    Optional<RefreshToken> findByUser(User user);

    // 토큰 문자열로 조회 — /auth/refresh 요청 시 쿠키 값으로 DB 검색할 때 사용
    Optional<RefreshToken> findByToken(String token);
}
