package com.dropie.security;

import com.dropie.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// OncePerRequestFilter : 하나의 요청에 대해 딱 한 번만 실행되는 필터
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

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

        // 4. 토큰 유효성 검증 (만료, 형식 오류, 서명 불일치 모두 여기서 잡음)
        // isExpired() 내부에서 parseSignedClaims()를 호출하는데,
        // 토큰이 만료됐거나 형식이 잘못된 경우 예외가 던져지므로 try-catch 필수
        // 필터는 GlobalExceptionHandler 범위 밖이라 BusinessException을 쓸 수 없음
        // → response에 직접 상태코드와 메시지를 써서 응답
        try {
            if (jwtTokenProvider.isExpired(token)) {
                // 만료된 토큰 → ErrorCode.EXPIRED_TOKEN 메시지와 함께 401 응답 후 종료
                sendErrorResponse(response, ErrorCode.EXPIRED_TOKEN);
                return;
            }
        } catch (Exception e) {
            // 형식이 잘못됐거나 서명이 다른 토큰 → ErrorCode.INVALID_TOKEN 메시지와 함께 401 응답 후 종료
            sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
            return;
        }

        // 5. 토큰에서 email 꺼내기
        String email = jwtTokenProvider.getEmail(token);

        // 6. DB에서 유저 정보 조회 (CustomUserDetails 형태로 반환됨)
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        // 7. Spring Security 인증 토큰 생성
        //    파라미터: (인증된유저정보, 비밀번호(null로 설정), 권한목록)
        //    세 번째 파라미터에 authorities를 넣어야 "인증됨" 상태가 됨
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // 8. SecurityContext에 인증 정보 저장
        //    → 이후 컨트롤러에서 @AuthenticationPrincipal 등으로 꺼낼 수 있음
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 9. 다음 필터로 넘기기
        filterChain.doFilter(request, response);
    }

    // 401 응답을 JSON 형태로 직접 작성하는 헬퍼 메서드
    // GlobalExceptionHandler가 필터까지 커버하지 못하기 때문에 별도로 처리
    // ErrorCode를 받아 메시지를 재사용함으로써 응답 형식 일관성 유지
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // JSON 형태로 응답하기 위해 Content-Type 설정
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\": \"" + errorCode.getMessage() + "\"}");
    }
}
