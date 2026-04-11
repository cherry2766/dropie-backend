package com.dropie.global.security;

import com.dropie.domain.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// UserDetails : Spring Security가 인증에 사용하는 인터페이스
// User 엔티티를 이 인터페이스 형태로 변환해주는 래퍼 클래스
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // 외부에서 User 엔티티 자체가 필요할 때 꺼내는 메서드
    public User getUser() {
        return user;
    }

    // 이 유저가 가진 권한 목록 반환
    // Spring Security는 권한을 "ROLE_USER", "ROLE_ADMIN" 형태로 인식함
    // → Role enum의 name() 앞에 "ROLE_" 붙여서 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    // Spring Security에서 "username" 이라고 부르지만
    // 우리 프로젝트는 email을 식별자로 쓰기 때문에 email 반환
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // 아래 4개는 계정 상태 관련 메서드
    // 지금은 별도 상태 관리 안 하므로 전부 true 반환
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
