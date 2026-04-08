package com.dropie.service.auth;


import com.dropie.domain.enums.Role;
import com.dropie.domain.user.User;
import com.dropie.dto.request.auth.LoginRequest;
import com.dropie.dto.request.auth.SignUpRequest;
import com.dropie.dto.response.auth.LoginResponse;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.repository.user.UserRepository;
import com.dropie.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

// @ExtendWith : JUnit5에 확장 기능을 붙이는 어노테이션
// MockitoExtension.class → "이 테스트 클래스에서 Mockito 사용할게" 라고 선언
// Spring Context를 전혀 띄우지 않아서 매우 빠름
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @InjectMocks : 테스트할 실제 대상 객체
    // 아래 @Mock으로 선언된 것들을 자동으로 이 안에 주입해줌
    // = new AuthService(userRepository, passwordEncoder, jwtTokenProvider) 와 동일한 효과
    @InjectMocks
    private AuthService authService;

    // @Mock : 가짜 객체 — 실제 DB/BCrypt/JWT 동작 없이 원하는 값을 리턴하도록 제어 가능
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("회원가입 성공")
    void 회원가입_성공() {

        // given : 테스트에 필요한 조건 세팅
        SignUpRequest request = new SignUpRequest("test@email.com", "pwd1234", "강체리");

        // given(모의객체.메서드(인자)).willReturn(리턴값)
        // → "이 메서드가 이 인자로 호출되면 이 값을 리턴해" 라고 Mock에게 지시
        given(userRepository.existsByEmail("test@email.com")).willReturn(false);
        given(passwordEncoder.encode("pwd1234")).willReturn("encoded_pwd");

        // anyString(), anyLong() → 어떤 값이 들어와도 상관없이 적용
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong()))
                .willReturn("jwt.token.here");

        // when : 실제 테스트 대상 메서드 실행
        LoginResponse response = authService.signUp(request);

        // then : 결과 검증
        // assertThat(실제값).isEqualTo(기댓값)
        assertThat(response.getAccessToken()).isEqualTo("jwt.token.here");

        // then(모의객체).should().메서드() → 해당 메서드가 실제로 호출됐는지 검증
        // save()가 1번 호출됐어야 정상 → 안 불렸으면 테스트 실패
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void 회원가입_중복이메일_예외() {
        // given
        SignUpRequest request = new SignUpRequest("test2@email.com", "pwd789", "강딸기");

        given(userRepository.existsByEmail("test2@email.com")).willReturn(true); // 중복 이메일 상황

        // when & then
        // assertThatThrownBy : 예외가 발생해야 하는 경우에 사용
        // () -> ... 안의 코드가 실행될 때 예외가 던져지는지 확인
        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(BusinessException.class)  // BusinessException 인지
                .extracting("errorCode")  // errorCode 필드 꺼내서
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);  // DUPLICATE_EMAIL 인지 확인
    }

    @Test
    @DisplayName("로그인 성공")
    void 로그인_성공() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pwd1234");

        // 실제 User 객체 생성
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("강체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pwd1234", "encoded_pwd")).willReturn(true);
        given(jwtTokenProvider.createToken(anyString(), anyString(), anyLong()))
                .willReturn("jwt.token.here");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("jwt.token.here");
    }

    @Test
    @DisplayName("로그인 실패 - 이메일 없음")
    void 로그인_이메일없음_예외() {
        // given
        LoginRequest request = new LoginRequest("test3@email.com", "pwd456");

        // Optional.empty() → findByEmail이 아무것도 못 찾은 상황
        given(userRepository.findByEmail("test3@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
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

        // matches(입력한평문, DB암호화값) → false 리턴 = 비밀번호 불일치 상황
        given(passwordEncoder.matches("wrongPw", "encoded_pwd")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}