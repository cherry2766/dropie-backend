package com.dropie.security;

import com.dropie.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// OncePerRequestFilter : 하나의 요청에 대해 딱 한 번만 실행되는 필터
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. 요청 헤더에서 Authorization 값 꺼내기
        String authorization = request.getHeader("Authorization");

        // 2. 헤더가 없거나 "Bearer "로 시작하지 않으면 → 토큰 없는 요청
        //    → 인증 처리 없이 다음 필터로 넘김 (permitAll 경로는 그냥 통과됨)
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return; // 아래 코드 실행 방지
        }

        // 3. "Bearer " (7글자) 이후의 순수 토큰 문자열만 추출
        String token = authorization.substring(7);

        // 4. 토큰 파싱 (parseSignedClaims 내부에서 만료/서명/형식 검증)
        // 필터는 GlobalExceptionHandler 범위 밖이라 직접 catch해서 response에 응답
        try {
            // 5. 토큰에서 email 꺼내기
            String email = jwtTokenProvider.getEmail(token);

            // 6. 토큰에서 role 꺼내서 SecurityContext에 인증 정보 저장
            String role = jwtTokenProvider.getRole(token);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // 7. 다음 필터로 넘기기
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            // 만료된 토큰
            sendErrorResponse(response, ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            // 형식이 잘못됐거나 서명이 다른 토큰
            sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
        }
    }

    // 에러 응답을 JSON 형태로 직접 작성하는 헬퍼 메서드
    // GlobalExceptionHandler가 필터까지 커버하지 못하기 때문에 별도로 처리
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\": \"" + errorCode.name() + "\", \"message\": \"" + errorCode.getMessage() + "\"}");
    }
}
