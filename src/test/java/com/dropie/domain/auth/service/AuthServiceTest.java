package com.dropie.domain.auth.service;

import com.dropie.domain.auth.entity.RefreshToken;
import com.dropie.domain.auth.repository.RefreshTokenRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.auth.dto.request.LoginRequest;
import com.dropie.domain.auth.dto.request.SignUpRequest;
import com.dropie.domain.auth.dto.response.LoginResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

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

    // HttpServletResponse는 Spring 컨텍스트 없이 쓸 수 없어서 Mockito로 직접 mock 생성
    private HttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = mock(HttpServletResponse.class);
    }

    // ===================== signUp =====================

    @Test
    @DisplayName("회원가입 성공")
    void 회원가입_성공() {
        // given
        SignUpRequest request = new SignUpRequest("test@email.com", "pwd1234", "강체리");

        given(userRepository.existsByEmail("test@email.com")).willReturn(false);
        given(passwordEncoder.encode("pwd1234")).willReturn("encoded_pwd");
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        // 기존 Refresh Token 없음 → 신규 INSERT 경로
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());

        // when
        LoginResponse response = authService.signUp(request, mockResponse);

        // then
        assertThat(response.getAccessToken()).isEqualTo("jwt.token.here");
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
        assertThatThrownBy(() -> authService.signUp(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("회원가입 성공 시 기존 Refresh Token이 있으면 rotate(UPDATE)됨")
    void 회원가입_기존_리프레시토큰_있으면_rotate() {
        // given
        SignUpRequest request = new SignUpRequest("test@email.com", "pwd1234", "강체리");

        given(userRepository.existsByEmail("test@email.com")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded_pwd");
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("new-refresh-token");

        // 이미 Refresh Token이 존재하는 상황 (재가입 방어 케이스는 아니지만 rotate 경로 검증)
        RefreshToken existingToken = mock(RefreshToken.class);
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.of(existingToken));

        // when
        authService.signUp(request, mockResponse);

        // then
        // 신규 save 대신 rotate(UPDATE)가 호출돼야 함
        then(existingToken).should().rotate(eq("new-refresh-token"), any(LocalDateTime.class));
        then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
    }

    // ===================== login =====================

    @Test
    @DisplayName("로그인 성공")
    void 로그인_성공() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong())).willReturn("jwt.token.here");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(refreshTokenRepository.findByUser(any())).willReturn(Optional.empty());

        // when
        LoginResponse response = authService.login(request, mockResponse);

        // then
        assertThat(response.getAccessToken()).isEqualTo("jwt.token.here");
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 없음")
    void 로그인_이메일없음_예외() {
        // given
        LoginRequest request = new LoginRequest("test3@email.com", "pwd456");
        given(userRepository.findByEmail("test3@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 틀림")
    void 로그인_비밀번호틀림_예외() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "wrongPw");
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강포도")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPw", "encoded_pwd")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request, mockResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
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
        // given
        // refreshTokenValue가 null인 경우 (쿠키 없이 로그아웃 요청)

        // when
        authService.logout(null, mockResponse);

        // then
        // null이면 DB 조회 자체를 하면 안 됨
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
