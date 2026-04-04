package com.dropie.security;

import com.dropie.domain.user.User;
import com.dropie.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// @RequiredArgsConstructor : final 필드들을 받는 생성자를 자동 생성해줌
// → 생성자 직접 안 써도 의존성 주입이 됨
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // Spring Security가 인증 시 자동으로 호출하는 메서드
    // 파라미터 이름이 username이지만 email을 전달
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // DB에서 email로 유저 조회
        User user = userRepository.findByEmail(email)
                // 없으면 예외 발생 → Spring Security가 인증 실패로 처리
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        // 찾은 유저를 CustomUserDetails로 감싸서 반환
        return new CustomUserDetails(user);
    }
}
