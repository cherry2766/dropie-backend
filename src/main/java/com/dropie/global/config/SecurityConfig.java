package com.dropie.global.config;

import com.dropie.global.security.CustomUserDetailsService;
import com.dropie.global.security.JwtAuthenticationFilter;
import com.dropie.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

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
                // 2. CORS 설정 적용 (corsConfigurationSource 빈 사용)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 3. 세션 비활성화, JWT 쓰면 서버가 상태를 저장할 필요 X (Stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 4. 요청별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()    // 회원가입, 로그인은 누구나 접근 가능
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()   // Swagger API 문서 — 비로그인 상태로도 접근 가능해야 문서 페이지가 정상적으로 뜸
                        .requestMatchers(HttpMethod.GET, "/events/**").permitAll() // 이벤트, 상품 조회 접근 가능
                        .requestMatchers("/ws-stomp/**").permitAll() // WebSocket 핸드셰이크 (재고 broadcast는 공개 정보)
                        .requestMatchers("/admin/**").hasRole("ADMIN") // 관리자만 접근 가능
                        .anyRequest().authenticated())         // 나머지는 로그인 필요
                // 5. 미인증 요청 시 401 반환
                // 토큰이 없으면 JwtAuthenticationFilter가 그냥 통과시키고
                // Spring Security 기본 동작은 403이므로 명시적으로 401로 설정
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                // 6. JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 등록
                // → 모든 요청에서 JWT 검증이 먼저 실행됨
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // 비밀번호 암호화 방식 등록 (AuthService에서 주입받아 사용)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS 설정
    // 브라우저는 다른 출처(Origin)로 요청할 때 서버가 허용했는지 먼저 확인함
    // 프론트(localhost:5173)와 백엔드(localhost:8080)는 포트가 달라 다른 출처로 취급되므로
    // 백엔드에서 명시적으로 프론트 주소를 허용해줘야 API 호출이 가능함
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));  // 프론트 개발 서버
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Authorization 헤더(JWT 토큰)를 프론트에서 읽을 수 있도록 허용
        config.setExposedHeaders(List.of("Authorization"));
        // 쿠키/인증 정보 포함 요청 허용 (JWT를 헤더로 보내므로 현재는 불필요하지만 확장성을 위해 설정)
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);  // 모든 경로에 적용
        return source;
    }
}
