package com.dropie.domain.auth.entity;

import com.dropie.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA는 기본 생성자 필요, 외부에서 직접 생성은 방지
public class RefreshToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 유저와 1:1 관계 — 유저 한 명당 Refresh Token 하나만 유지
    // unique = true : DB 레벨에서도 중복 방지
    // (여러 기기 동시 로그인 허용하려면 나중에 1:N으로 변경하면 됨)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public RefreshToken(User user, String token, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    // 만료 여부 확인
    // LocalDateTime.now()가 expiresAt 이후이면 만료된 것
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    // 토큰 교체 (Refresh Token Rotation)
    // 새로운 행을 INSERT 하지 않고 기존 행을 UPDATE → DB 행 낭비 없음
    public void rotate(String newToken, LocalDateTime newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
    }

}
