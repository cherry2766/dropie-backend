package com.dropie.domain.auth.service;

import com.dropie.domain.auth.entity.RefreshToken;
import com.dropie.domain.auth.repository.RefreshTokenRepository;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
import com.dropie.domain.auth.dto.response.SignUpResponse;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.email.EmailVerificationService;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private UserPreferenceRepository preferenceRepository;

    // HttpServletResponse는 Spring 컨텍스트 없이 쓸 수 없어서 Mockito로 직접 mock 생성
    private HttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = mock(HttpServletResponse.class);
    }

    // ===================== signUp =====================

    @Test
    @DisplayName("회원가입 성공 - 인증 메일 발송 후 안내 메시지 반환")
    void 회원가입_성공() {
        // given
        SignUpRequest request = new SignUpRequest("test@email.com", "pwd1234", "강체리");

        given(userRepository.existsByEmail("test@email.com")).willReturn(false);
        given(passwordEncoder.encode("pwd1234")).willReturn("encoded_pwd");

        // when
        // signUp은 이제 HttpServletResponse 파라미터 없음 (토큰 발급 안 하므로)
        SignUpResponse response = authService.signUp(request);

        // then
        assertThat(response.getMessage()).isEqualTo("인증 이메일을 발송했습니다. 메일을 확인해 주세요.");
        assertThat(response.getEmail()).isEqualTo("test@email.com");
        then(userRepository).should().save(any(User.class));
        then(emailVerificationService).should().sendVerificationEmail("test@email.com");
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void 회원가입_중복이메일_예외() {
        // given
        SignUpRequest request = new SignUpRequest("test2@email.com", "pwd789", "강딸기");
        given(userRepository.existsByEmail("test2@email.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    // ===================== login =====================

    @Test
    @DisplayName("로그인 성공 - 차단 안 된 상태에서 정상 로그인 후 실패 횟수 초기화")
    void 로그인_성공() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .emailVerified(true)
                .build();

        // 차단 상태 아님 (login_block 키 없음)
        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());
        given(preferenceRepository.existsByUser(any())).willReturn(false);

        // when
        LoginResponse response = authService.login(request, mockResponse);

        // then
        assertThat(response.getAccessToken()).isEqualTo("jwt.token.here");
        // 로그인 성공 시 실패 횟수 키 + 차단 키 모두 삭제돼야 함
        then(redisTemplate).should().delete("login_fail:test@email.com");
        then(redisTemplate).should().delete("login_block:test@email.com");
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 없음")
    void 로그인_이메일없음_예외() {
        // given
        LoginRequest request = new LoginRequest("test3@email.com", "pwd456");
        given(redisTemplate.hasKey("login_block:test3@email.com")).willReturn(false);
        given(userRepository.findByEmail("test3@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 인증 미완료")
    void 이메일_미인증_로그인_예외() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        // emailVerified 기본값이 false이므로 별도 설정 없이 미인증 상태
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .build();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);

        // when & then
        // 비밀번호는 맞지만 이메일 인증을 안 했으면 로그인 차단
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 틀림 (1회 실패, 미차단)")
    void 로그인_비밀번호틀림_예외() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "wrongPw");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강포도")
                .role(Role.USER)
                .build();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPw", "encoded_pwd")).willReturn(false);
        // 1회 실패 → MAX_FAIL(5)에 미달이므로 차단 키 생성 안 됨
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("login_fail:test@email.com")).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("로그인 실패 - 차단된 계정이면 LOGIN_BLOCKED 예외")
    void 로그인_차단_상태면_LOGIN_BLOCKED_예외() {
        // given
        // login_block 키가 Redis에 존재 → 15분 차단 상태
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(true);

        // when & then
        // 차단 상태면 비밀번호 확인도 하지 않고 즉시 예외
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_BLOCKED);
    }

    @Test
    @DisplayName("로그인 5회 실패 시 차단 키 생성 - 이후 15분간 로그인 불가")
    void 로그인_5회_실패_시_차단키_생성() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "wrongPw");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강포도")
                .role(Role.USER)
                .build();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPw", "encoded_pwd")).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // 5번째 실패 → MAX_FAIL(5) 도달 → 차단 키 생성 트리거
        given(valueOperations.increment("login_fail:test@email.com")).willReturn(5L);

        // when & then
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        // 5회 도달 시 login_block 키가 생성됐는지 확인 (TTL 15분)
        then(valueOperations).should().set(
                eq("login_block:test@email.com"),
                eq("1"),
                eq(Duration.ofMinutes(15))
        );
    }

    @Test
    @DisplayName("로그인 성공 시 실패 횟수 초기화 - 이전 실패 기록이 남아있어도 성공하면 삭제")
    void 로그인_성공_시_실패횟수_초기화() {
        // given
        // 4번 실패했다가 5번째에 성공하는 시나리오
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .emailVerified(true)
                .build();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());
        given(preferenceRepository.existsByUser(any())).willReturn(false);

        // when
        authService.login(request, mockResponse);

        // then - login_fail 키와 login_block 키 모두 삭제 확인
        then(redisTemplate).should().delete("login_fail:test@email.com");
        then(redisTemplate).should().delete("login_block:test@email.com");
    }

    // ===================== showOnboarding =====================

    @Test
    @DisplayName("로그인 성공 - 취향 없고 스킵 안 한 신규 유저는 showOnboarding true")
    void 로그인_성공_온보딩_노출() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .emailVerified(true)
                .build();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());
        // 취향 없음 + 스킵 안 함 → showOnboarding = true
        given(preferenceRepository.existsByUser(any())).willReturn(false);

        // when
        LoginResponse response = authService.login(request, mockResponse);

        // then
        assertThat(response.isShowOnboarding()).isTrue();
    }

    @Test
    @DisplayName("로그인 성공 - 취향 있는 유저는 showOnboarding false")
    void 로그인_성공_취향있으면_온보딩_미노출() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .emailVerified(true)
                .build();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());
        // 취향 있음 → showOnboarding = false
        given(preferenceRepository.existsByUser(any())).willReturn(true);

        // when
        LoginResponse response = authService.login(request, mockResponse);

        // then
        assertThat(response.isShowOnboarding()).isFalse();
    }

    @Test
    @DisplayName("로그인 성공 - 온보딩 스킵한 유저는 showOnboarding false")
    void 로그인_성공_스킵한유저_온보딩_미노출() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .emailVerified(true)
                .build();
        // 스킵 버튼을 눌러 onboardingSkipped = true 상태
        user.skipOnboarding();

        given(redisTemplate.hasKey("login_block:test@email.com")).willReturn(false);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());
        // 취향은 없지만 스킵했으므로 → showOnboarding = false
        given(preferenceRepository.existsByUser(any())).willReturn(false);

        // when
        LoginResponse response = authService.login(request, mockResponse);

        // then
        assertThat(response.isShowOnboarding()).isFalse();
    }

    // ===================== refresh =====================

    @Test
    @DisplayName("토큰 재발급 성공 - 유효한 Refresh Token이면 새 Access Token 반환 및 Rotation")
    void 토큰_재발급_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pw")
                .nickname("체리")
                .role(Role.USER)
                .build();

        // RefreshToken은 private 필드라 mock으로 동작 제어
        RefreshToken refreshToken = mock(RefreshToken.class);
        given(refreshTokenRepository.findByToken("valid-refresh-token")).willReturn(Optional.of(refreshToken));
        given(refreshToken.isExpired()).willReturn(false);
        given(refreshToken.getUser()).willReturn(user);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("new-refresh-token");
        // refresh()는 issueTokens()를 거치지 않고 직접 응답을 만들므로 preferenceRepository 미호출

        // when
        LoginResponse response = authService.refresh("valid-refresh-token", mockResponse);

        // then
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        // Rotation: 기존 토큰이 새 값으로 교체됐는지 확인
        then(refreshToken).should().rotate(eq("new-refresh-token"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - DB에 없는 토큰이면 INVALID_TOKEN 예외")
    void 토큰_재발급_존재하지않는_토큰_예외() {
        // given
        // 탈취 후 이미 삭제됐거나 처음부터 없는 토큰
        given(refreshTokenRepository.findByToken("invalid-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refresh("invalid-token", mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 만료된 토큰이면 EXPIRED_TOKEN 예외 및 DB에서 삭제")
    void 토큰_재발급_만료된_토큰_예외() {
        // given
        RefreshToken refreshToken = mock(RefreshToken.class);
        given(refreshTokenRepository.findByToken("expired-token")).willReturn(Optional.of(refreshToken));
        // isExpired()가 true → 만료된 토큰 상황
        given(refreshToken.isExpired()).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.refresh("expired-token", mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);

        // 만료된 토큰은 DB에서 삭제돼야 함 (다음 요청 시 INVALID_TOKEN으로 처리)
        then(refreshTokenRepository).should().delete(refreshToken);
    }

    // ===================== logout =====================

    @Test
    @DisplayName("로그아웃 성공 - Refresh Token이 DB에서 삭제됨")
    void 로그아웃_성공() {
        // given
        RefreshToken refreshToken = mock(RefreshToken.class);
        given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(refreshToken));

        // when
        authService.logout("valid-token", mockResponse);

        // then
        then(refreshTokenRepository).should().delete(refreshToken);
    }

    @Test
    @DisplayName("로그아웃 - 쿠키가 없어도 예외 없이 정상 처리")
    void 로그아웃_토큰없이_호출() {
        // when
        authService.logout(null, mockResponse);

        // then - null이면 DB 조회 자체를 하면 안 됨
        then(refreshTokenRepository).should(never()).findByToken(any());
    }

    @Test
    @DisplayName("로그아웃 - DB에 없는 토큰이어도 예외 없이 정상 처리")
    void 로그아웃_존재하지않는_토큰() {
        // given
        // 이미 만료/삭제된 토큰으로 로그아웃 시도 → findByToken이 empty 반환
        given(refreshTokenRepository.findByToken("unknown-token")).willReturn(Optional.empty());

        // when & then
        // 예외 없이 정상 완료돼야 함 (ifPresent이므로 delete 미호출)
        assertThatCode(() -> authService.logout("unknown-token", mockResponse))
                .doesNotThrowAnyException();
        then(refreshTokenRepository).should(never()).delete(any());
    }

    // ===================== verifyEmail =====================

    @Test
    @DisplayName("이메일 인증 성공 - emailVerified가 true로 저장됨")
    void 이메일_인증_성공() {
        // given
        given(emailVerificationService.verifyToken("valid-token")).willReturn("test@email.com");

        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pw")
                .nickname("체리")
                .role(Role.USER)
                .build();
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when
        authService.verifyEmail("valid-token");

        // then
        then(userRepository).should().save(user);
    }

    @Test
    @DisplayName("이메일 인증 실패 - 만료된 토큰이면 INVALID_VERIFICATION_TOKEN 예외")
    void 이메일_인증_토큰없음_예외() {
        // given
        given(emailVerificationService.verifyToken("expired-token")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_VERIFICATION_TOKEN);
    }

    @Test
    @DisplayName("이미 인증된 유저가 링크 재클릭 시 save 호출 안 함")
    void 이미_인증된_유저_재인증_무시() {
        // given
        given(emailVerificationService.verifyToken("valid-token")).willReturn("test@email.com");

        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pw")
                .nickname("체리")
                .role(Role.USER)
                .build();
        user.verifyEmail();
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when
        authService.verifyEmail("valid-token");

        // then
        then(userRepository).should(never()).save(any());
    }
}
