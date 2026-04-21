package com.dropie.domain.auth.service;

import com.dropie.domain.auth.dto.response.SignUpResponse;
import com.dropie.domain.auth.entity.RefreshToken;
import com.dropie.domain.auth.repository.RefreshTokenRepository;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
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
import com.dropie.global.exception.custom.UserWithdrawnException;
import com.dropie.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redisTemplate;
    private final UserPreferenceRepository preferenceRepository;

    // Access Token 만료: 15분 (밀리초 단위)
    private static final long ACCESS_TOKEN_EXPIRY_MS = 900_000L;
    // Refresh Token 만료: 7일
    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;

    private static final String FAIL_PREFIX  = "login_fail:";
    private static final String BLOCK_PREFIX = "login_block:";
    private static final int    MAX_FAIL     = 5;
    private static final Duration BLOCK_TTL  = Duration.ofMinutes(15);

    // 회원가입
    // → 유저 생성 + 인증 메일 발송만 하고, 토큰은 발급하지 않음
    // → 이메일 인증 완료 후 로그인해야 서비스 이용 가능
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        log.debug("[signUp] 요청 들어옴 - email: {}", request.getEmail());

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("[signUp] 이메일 중복 - email: {}", request.getEmail());
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 닉네임 중복 확인
        if (userRepository.existsByNickname(request.getNickname())) {
            log.warn("[signUp] 닉네임 중복 - nickname: {}", request.getNickname());
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 유저 생성
        // → emailVerified는 @Builder.Default로 기본값이 false
        // → 인증 완료 전까지 emailVerified = false 상태로 DB에 저장됨
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(Role.USER)
                .build();

        userRepository.save(user);
        log.info("[signUp] 유저 생성 완료 (미인증 상태) - email: {}", user.getEmail());

        // 인증 이메일 발송
        // → @Async가 붙어 있어 별도 스레드에서 실행됨 (응답 속도에 영향 없음)
        emailVerificationService.sendVerificationEmail(user.getEmail());

        // 토큰 발급 없이 안내 메시지만 반환
        // → 사용자는 메일 확인 → 링크 클릭 → 인증 완료 → 로그인 순서로 진행
        return SignUpResponse.builder()
                .message("인증 이메일을 발송했습니다. 메일을 확인해 주세요.")
                .email(user.getEmail())
                .build();
    }

    // 로그인
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        log.debug("[login] 요청 들어옴 - email: {}", request.getEmail());
        checkLoginBlock(request.getEmail());  // 차단 여부 먼저 확인

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("[login] 존재하지 않는 이메일 - email: {}", request.getEmail());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        // 탈퇴한 계정 확인
        // → deletedAt이 null이 아니면 소프트 딜리트된 유저 → 로그인 차단
        if (user.getDeletedAt() != null) {
            log.warn("[login] 탈퇴한 계정 로그인 시도 - email: {}", request.getEmail());
            throw new UserWithdrawnException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[login] 비밀번호 불일치 - email: {}", request.getEmail());
            recordLoginFailure(request.getEmail());  // 실패 시 카운트 증가
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 이메일 인증을 완료해야만 로그인 허용 — 미인증 유저가 API 직접 호출로 우회하는 것도 차단
        if (!user.isEmailVerified()) {
            log.warn("[login] 이메일 미인증 유저 로그인 시도 - email: {}", user.getEmail());
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        clearLoginFailure(request.getEmail());  // 성공 시 실패 기록 초기화

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

        // 취향 데이터가 없고 스킵도 안 했으면 온보딩 노출
        boolean hasPreferences = preferenceRepository.existsByUser(user);
        boolean showOnboarding = !hasPreferences && !user.isOnboardingSkipped();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .role(user.getRole().name())
                .showOnboarding(showOnboarding)
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

    // 로그인 차단 상태 확인
    // → login_block 키가 있으면 15분 동안 로그인 불가
    private void checkLoginBlock(String email) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLOCK_PREFIX + email))) {
            throw new BusinessException(ErrorCode.LOGIN_BLOCKED);
        }
    }

    // 로그인 실패 처리
    // → 실패 횟수 증가, 5회 도달 시 차단 키 생성
    private void recordLoginFailure(String email) {
        String failKey = FAIL_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(failKey);
        // 실패할 때마다 TTL 갱신 (마지막 실패로부터 15분)
        redisTemplate.expire(failKey, BLOCK_TTL);

        if (count != null && count >= MAX_FAIL) {
            redisTemplate.opsForValue().set(BLOCK_PREFIX + email, "1", BLOCK_TTL);
        }
    }

    // 로그인 성공 시 실패 기록 초기화
    private void clearLoginFailure(String email) {
        redisTemplate.delete(FAIL_PREFIX + email);
        redisTemplate.delete(BLOCK_PREFIX + email);
    }

    public void resendVerification(String email) {
        log.debug("[resendVerification] 재발송 요청 - email: {}", email);

        // 가입된 유저인지 확인 (존재하지 않는 이메일로 요청 방지)
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        // 이미 인증된 유저가 재발송 요청하는 경우 무시
        if (user.isEmailVerified()) {
            log.debug("[resendVerification] 이미 인증된 유저 - email: {}", email);
            return;
        }

        emailVerificationService.resendVerificationEmail(email);
        log.info("[resendVerification] 인증 메일 재발송 완료 - email: {}", email);
    }
}
