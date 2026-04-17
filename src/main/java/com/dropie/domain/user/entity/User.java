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
}
