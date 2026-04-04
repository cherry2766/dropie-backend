package com.dropie.config;

import com.dropie.security.JwtAuthenticationFilter;
import com.dropie.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    // AuthenticationManager : 실제 인증(아이디/비밀번호 검증)을 수행하는 객체
    // AuthService에서 로그인 처리할 때 사용
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 비활성화, 세션 방식이 아니기 때문에 공격 위험 없음
                .csrf(csrf -> csrf.disable())
                // 2. 세션 비활성화, JWT 쓰면 서버가 상태를 저장할 필요 X (Stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. 요청별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()    // 회원가입, 로그인은 누구나 접근 가능
                        .requestMatchers(HttpMethod.GET, "/events/**").permitAll() // 이벤트, 상품 조회 접근 가능
                        .requestMatchers("/admin/**").hasRole("ADMIN") // 관리자만 접근 가능
                        .anyRequest().authenticated())         // 나머지는 로그인 필요
                // 4. JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 등록
                // → 모든 요청에서 JWT 검증이 먼저 실행됨
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // 5. 비밀번호 암호화 방식 등록 (AuthService에서 주입받아 사용)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
