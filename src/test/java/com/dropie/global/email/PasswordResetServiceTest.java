package com.dropie.global.email;

import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Mock private JavaMailSender mailSender;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private MimeMessage mimeMessage;
    // HTML 본문을 Thymeleaf 템플릿으로 렌더링하는 공용 서비스 (리팩토링으로 추가된 의존성)
    @Mock private EmailTemplateService emailTemplateService;

    @BeforeEach
    void setUp() {
        // @Value로 주입되는 fromEmail은 Spring 컨텍스트 없이 주입 안 됨
        // → ReflectionTestUtils로 private 필드에 직접 값 주입
        ReflectionTestUtils.setField(passwordResetService, "fromEmail", "test@test.com");
        // @Value("${app.frontend-url}")도 컨텍스트 없이 주입 안 되므로 직접 주입
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://localhost:5173");
    }

    // ===================== requestPasswordReset =====================

    @Test
    @DisplayName("비밀번호 재설정 요청 성공 - 가입된 이메일이면 메일 발송")
    void 비밀번호_재설정_요청_이메일_존재하면_메일발송() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // sendResetEmail()이 내부에서 호출하므로 Redis·메일 stub 필요
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        // 템플릿 렌더링 결과는 테스트 관심사가 아니므로 빈 HTML 문자열만 반환하도록 stub
        // → MimeMessageHelper.setText()가 null을 받으면 NPE가 발생하므로 non-null 값 필수
        given(emailTemplateService.render(anyString(), anyMap())).willReturn("<html></html>");

        // when
        passwordResetService.requestPasswordReset("test@email.com");

        // then - 실제로 메일이 발송됐는지 확인
        then(mailSender).should().send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청 - 가입되지 않은 이메일이어도 예외 없이 200 응답 (이메일 열거 공격 방지)")
    void 비밀번호_재설정_요청_이메일_없어도_예외없음() {
        // given
        // 이메일이 없으면 ifPresent가 실행되지 않으므로 메일 발송 안 됨
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then - 예외 없이 정상 종료
        assertThatCode(() -> passwordResetService.requestPasswordReset("ghost@email.com"))
                .doesNotThrowAnyException();

        // 메일은 절대 발송되면 안 됨
        then(mailSender).should(never()).send(any(MimeMessage.class));
    }

    // ===================== sendResetEmail =====================

    @Test
    @DisplayName("재설정 메일 발송 - Redis에 토큰 저장(30분 TTL) 및 HTML 메일 전송")
    void 재설정_메일_발송_토큰저장_및_메일전송() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        // 템플릿 렌더링 결과는 테스트 관심사가 아니므로 빈 HTML 문자열만 반환하도록 stub
        given(emailTemplateService.render(anyString(), anyMap())).willReturn("<html></html>");

        // when
        // @Async 메서드지만 단위 테스트에선 Spring 컨텍스트가 없으므로 동기로 실행됨
        passwordResetService.sendResetEmail("test@email.com");

        // then
        // 1. Redis에 "pwd_reset:{UUID}" → 이메일이 30분 TTL로 저장됐는지 확인
        //    UUID는 매번 달라지므로 키는 anyString(), 값만 정확히 검증
        then(valueOperations).should().set(
                anyString(),
                eq("test@email.com"),
                eq(Duration.ofMinutes(30))
        );

        // 2. MimeMessage(HTML 메일)로 발송됐는지 확인
        then(mailSender).should().send(any(MimeMessage.class));
    }

    // ===================== resetPassword =====================

    @Test
    @DisplayName("비밀번호 재설정 성공 - 새 비밀번호로 변경되고 사용한 토큰 즉시 삭제")
    void 비밀번호_재설정_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("old_encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("pwd_reset:valid-token")).willReturn("test@email.com");
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPwd1234")).willReturn("new_encoded_pwd");

        // when
        passwordResetService.resetPassword("valid-token", "newPwd1234");

        // then
        // 실제 User 객체의 password가 변경됐는지 확인
        assertThat(user.getPassword()).isEqualTo("new_encoded_pwd");

        // 같은 토큰으로 비밀번호를 여러 번 바꾸지 못하도록 토큰이 삭제됐는지 확인
        then(redisTemplate).should().delete("pwd_reset:valid-token");
    }

    @Test
    @DisplayName("비밀번호 재설정 실패 - 토큰이 없거나 만료됐으면 PASSWORD_RESET_TOKEN_INVALID 예외")
    void 비밀번호_재설정_토큰_없음_예외() {
        // given
        // Redis에 해당 키가 없거나 이미 만료된 상황 → null 반환
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        // when & then
        assertThatThrownBy(() -> passwordResetService.resetPassword("expired-token", "newPwd1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        // 토큰 무효이므로 DB 조회·비밀번호 변경이 일어나면 안 됨
        then(userRepository).should(never()).findByEmail(any());
    }

    @Test
    @DisplayName("비밀번호 재설정 실패 - 토큰은 유효하지만 유저가 없으면 USER_NOT_FOUND 예외")
    void 비밀번호_재설정_유저없음_예외() {
        // given
        // 토큰은 Redis에 존재하지만, 해당 이메일의 유저가 DB에 없는 경우 (탈퇴·삭제 등)
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("pwd_reset:valid-token")).willReturn("ghost@email.com");
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> passwordResetService.resetPassword("valid-token", "newPwd1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 재설정 성공 후 동일 토큰 재사용 불가 - 토큰 삭제로 멱등성 깨짐")
    void 비밀번호_재설정_토큰_재사용_불가() {
        // given
        // 첫 번째 요청: 토큰 유효 → 성공
        // 두 번째 요청: 토큰 삭제됐으므로 null → 실패
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("pwd_reset:one-time-token"))
                .willReturn("test@email.com")  // 1회차: 정상
                .willReturn(null);             // 2회차: 삭제된 후

        User user = User.builder()
                .email("test@email.com")
                .password("old_encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode(any())).willReturn("encoded");

        // when - 1회차 성공
        passwordResetService.resetPassword("one-time-token", "newPwd1234");

        // then - 2회차: 토큰이 없으므로 예외
        assertThatThrownBy(() -> passwordResetService.resetPassword("one-time-token", "anotherPwd1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
}
