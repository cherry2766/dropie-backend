package com.dropie.domain.auth.service;

import com.dropie.domain.auth.entity.RefreshToken;
import com.dropie.domain.auth.repository.RefreshTokenRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.global.email.EmailVerificationService;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.UserNotFoundException;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenRepository refreshTokenRepository;

    // Access Token 만료: 15분 (밀리초 단위)
    private static final long ACCESS_TOKEN_EXPIRY_MS = 900_000L;
    // Refresh Token 만료: 7일
    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;

    // 회원가입 후 바로 JWT 발급
    // → 가입 즉시 로그인 상태로 서비스 진입 가능
    @Transactional
    public LoginResponse signUp(SignUpRequest request, HttpServletResponse response) {
        log.debug("[signUp] 요청 들어옴 - email: {}", request.getEmail());

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("[signUp] 이메일 중복 - email: {}", request.getEmail());
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        // User 엔티티 생성 (빌더 패턴 사용 - User 클래스에 @Builder 있음)
        User user = User.builder()
                .email(request.getEmail())
                // 비밀번호는 평문으로 저장하면 안 됨 → BCrypt로 암호화해서 저장
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(Role.USER)
                .build();

        userRepository.save(user);
        log.info("[signUp] 회원가입 완료 - email: {}, nickname: {}", user.getEmail(), user.getNickname());

        // 회원가입 후 이메일 인증 메일 발송 (비동기)
        // → sendVerificationEmail()에 @Async가 붙어 있어 별도 스레드에서 실행됨
        //   메일 발송 지연이 응답 속도에 영향을 주지 않음
        emailVerificationService.sendVerificationEmail(user.getEmail());

        // Access Token + Refresh Token 발급 후 쿠키 설정
        return issueTokens(user, response);
    }

    // 로그인
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        log.debug("[login] 요청 들어옴 - email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("[login] 존재하지 않는 이메일 - email: {}", request.getEmail());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[login] 비밀번호 불일치 - email: {}", request.getEmail());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("[login] 로그인 성공 - email: {}", user.getEmail());
        return issueTokens(user, response);
    }

    // 토큰 재발급 (POST /auth/refresh)
    // 브라우저가 자동으로 첨부한 쿠키의 Refresh Token을 검증 후 새 Access Token 발급
    @Transactional
    public LoginResponse refresh(String refreshTokenValue, HttpServletResponse response) {
        log.debug("[refresh] 토큰 재발급 요청");

        // DB에서 토큰 조회 — 없으면 탈취됐거나 이미 만료 처리된 토큰
        RefreshToken rt = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        // 만료 여부 확인
        if (rt.isExpired()) {
            // 만료된 토큰은 DB에서 삭제 (다음 요청 시 INVALID_TOKEN으로 처리됨)
            refreshTokenRepository.delete(rt);
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = rt.getUser();

        // Refresh Token Rotation: 재발급 시 Refresh Token도 함께 교체
        // → 탈취된 토큰으로 재발급 시도 시 DB와 불일치 → 즉시 감지 가능
        String newAccessToken = jwtTokenProvider.createToken(
                user.getEmail(), user.getRole().name(), ACCESS_TOKEN_EXPIRY_MS
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken();

        rt.rotate(newRefreshToken, LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS));
        setRefreshTokenCookie(response, newRefreshToken);

        log.info("[refresh] 토큰 재발급 완료 - userId: {}", user.getId());
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .role(user.getRole().name())
                .build();
    }

    // 로그아웃 (POST /auth/logout)
    @Transactional
    public void logout(String refreshTokenValue, HttpServletResponse response) {
        log.debug("[logout] 로그아웃 요청");

        // DB에서 Refresh Token 삭제
        if (refreshTokenValue != null) {
            refreshTokenRepository.findByToken(refreshTokenValue)
                    .ifPresent(refreshTokenRepository::delete);
        }

        // 쿠키 즉시 만료 처리 (maxAge=0 → 브라우저가 응답 받으면 즉시 쿠키 삭제)
        ResponseCookie expired = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)   // 로컬 개발용, 배포 시 true로 변경
                .path("/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        log.info("[logout] 로그아웃 완료");
    }

    // 이메일 인증 처리
    // → GET /auth/verify-email?token=xxx 클릭 시 호출됨
    @Transactional
    public void verifyEmail(String token) {
        // 토큰으로 이메일 조회 — 없거나 만료됐으면 null
        String email = emailVerificationService.verifyToken(token);

        if (email == null) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        // 이미 인증된 유저가 링크를 다시 클릭한 경우 무시 (멱등성 보장)
        if (!user.isEmailVerified()) {
            user.verifyEmail();
            userRepository.save(user);
            log.info("[verifyEmail] 이메일 인증 완료 - email: {}", email);
        } else {
            log.debug("[verifyEmail] 이미 인증된 유저 재시도 - email: {}", email);
        }
    }

    // Access Token + Refresh Token 발급 + Refresh Token을 쿠키로 설정
    // login, signUp 양쪽에서 공통으로 사용하기 위해 분리
    private LoginResponse issueTokens(User user, HttpServletResponse response) {
        String accessToken = jwtTokenProvider.createToken(
                user.getEmail(), user.getRole().name(), ACCESS_TOKEN_EXPIRY_MS
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        // DB에 Refresh Token 저장
        // ifPresentOrElse: 이미 있으면 rotate(UPDATE), 없으면 새로 INSERT
        refreshTokenRepository.findByUser(user)
                .ifPresentOrElse(
                        rt -> rt.rotate(refreshToken, LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS)),
                        () -> refreshTokenRepository.save(
                                RefreshToken.builder()
                                        .user(user)
                                        .token(refreshToken)
                                        .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS))
                                        .build()


                        )
                );
        setRefreshTokenCookie(response, refreshToken);
        return LoginResponse.builder()
                .accessToken(accessToken)
                .role(user.getRole().name())
                .build();
    }

    // Refresh Token을 httpOnly Cookie로 설정
    // 여러 메서드에서 공통으로 쓰이므로 별도 메서드로 분리
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)         // JS 접근 불가 → XSS 공격으로 탈취 불가
                .secure(false)          // 로컬 개발 환경 (HTTP) → 배포 시 true로 변경
                .path("/auth")          // /auth 요청에만 쿠키 첨부 → 일반 API 요청엔 미첨부
                .maxAge(Duration.ofDays(REFRESH_TOKEN_EXPIRY_DAYS))
                .sameSite("Strict")     // 같은 사이트 요청에서만 전송 → CSRF 공격 방지
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
