package com.dropie.domain.auth.service;

import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.global.email.EmailVerificationService;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;

    // 회원가입 후 바로 JWT 발급
    // → 가입 즉시 로그인 상태로 서비스 진입 가능
    public LoginResponse signUp(SignUpRequest request) {
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

        // 저장된 유저 정보로 바로 토큰 발급
        // → 회원가입과 로그인을 한 번에 처리
        return generateTokenResponse(user);
    }

    // 로그인
    public LoginResponse login(LoginRequest request) {
        log.debug("[login] 요청 들어옴 - email: {}", request.getEmail());

        // 1. 이메일로 유저 조회 - 없으면 INVALID_CREDENTIALS
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("[login] 존재하지 않는 이메일 - email: {}", request.getEmail());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        // 2. 비밀번호 검증 - 틀리면 INVALID_CREDENTIALS
        // passwordEncoder.matches(입력한평문, DB의암호화된비밀번호)
        // → 내부적으로 입력값을 암호화해서 비교함
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[login] 비밀번호 불일치 - email: {}", request.getEmail());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        log.info("[login] 로그인 성공 - email: {}", user.getEmail());
        // 3. JWT 발급
        return generateTokenResponse(user);
    }

    // 이메일 인증 처리
    // → GET /auth/verify-email?token=xxx 클릭 시 호출됨
    public void verifyEmail(String token) {
        // 토큰으로 이메일 조회 — 없거나 만료됐으면 null
        String email = emailVerificationService.verifyToken(token);

        if (email == null) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 인증된 유저가 링크를 다시 클릭한 경우 무시 (멱등성 보장)
        if (!user.isEmailVerified()) {
            user.verifyEmail();
            userRepository.save(user);
            log.info("[verifyEmail] 이메일 인증 완료 - email: {}", email);
        } else {
            log.debug("[verifyEmail] 이미 인증된 유저 재시도 - email: {}", email);
        }
    }

    // JWT 토큰 발급 후 LoginResponse 반환
    private LoginResponse generateTokenResponse(User user) {
        String token = jwtTokenProvider.createToken(
                user.getEmail(),
                user.getRole().name(),
                1800000L
        );
        return LoginResponse.builder().accessToken(token).build();
    }
}
