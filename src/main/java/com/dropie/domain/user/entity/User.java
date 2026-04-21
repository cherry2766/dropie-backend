package com.dropie.domain.user.entity;

import com.dropie.domain.address.entity.Address;
import com.dropie.global.common.BaseEntity;
import com.dropie.domain.order.entity.Order;
import com.dropie.domain.preference.entity.UserPreference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 이메일 인증 여부
    // → 가입 직후 false, 인증 링크 클릭 후 true로 변경
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    // 온보딩 스킵 여부
    // → 스킵 버튼 클릭 시 true로 변경, 이후 로그인부터 온보딩 미노출
    // → 중간 이탈(창 닫기 등)은 false 유지 → 다음 로그인에 다시 노출
    @Builder.Default
    @Column(nullable = false)
    private boolean onboardingSkipped = false;

    // null 허용 - 가입 직후에는 이미지 없을 수 있음
    private String profileImageUrl;

    @OneToMany(mappedBy = "user")
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<UserPreference> preferences = new ArrayList<>();

    // 이메일 인증 완료 처리 메서드
    public void verifyEmail() {
        this.emailVerified = true;
    }

    // 온보딩 스킵 처리
    public void skipOnboarding() {
        this.onboardingSkipped = true;
    }

    // 비밀번호 재설정 시 호출 — 이미 암호화된 값을 받아서 교체
    // PasswordEncoder.encode()는 서비스 레이어에서 처리하고 여기서는 단순 교체만 담당
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 회원 탈퇴 처리 메서드
    // → DB에서 행을 삭제하지 않고 deletedAt에 현재 시간을 기록하는 소프트 딜리트 방식
    // → 탈퇴 후에도 주문/이력 데이터를 보존할 수 있고, 실수로 탈퇴한 경우 복구도 가능
    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }

    // 닉네임 수정
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // 프로필 이미지 url 저장/변경
    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
