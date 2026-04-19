package com.dropie.global.email;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    // opsForValue()가 null을 반환하지 않도록 ValueOperations Mock을 별도 선언
    // → redisTemplate.opsForValue().set() 같은 체인 호출이 가능해짐
    @Mock
    private ValueOperations<String, String> valueOperations;

    // MimeMessage는 JavaMailSender.createMimeMessage()의 반환값
    // → MimeMessageHelper가 이 객체에 발신자/수신자/본문을 설정함
    @Mock
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        // @Value("${spring.mail.username}")는 Spring 컨텍스트 없이 주입되지 않음
        // → ReflectionTestUtils로 private 필드에 직접 값을 주입해서 NullPointerException 방지
        ReflectionTestUtils.setField(emailVerificationService, "fromEmail", "test@test.com");

        // opsForValue(), createMimeMessage() stub은 테스트마다 필요 여부가 달라서 각 테스트에 직접 선언
        // → setUp()에 두면 일부 테스트에서 사용 안 해 UnnecessaryStubbingException 발생
    }

    // ===================== sendVerificationEmail =====================

    @Test
    @DisplayName("인증 이메일 발송 - Redis에 토큰 저장 및 HTML 메일 전송")
    void 인증메일_발송_성공() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);

        // when
        emailVerificationService.sendVerificationEmail("test@email.com");

        // then
        // 1. Redis에 토큰이 저장됐는지 확인
        //    토큰이 UUID라 정확한 키를 알 수 없으므로 any()로 검증
        then(valueOperations).should().set(
                anyString(),
                eq("test@email.com"),
                eq(Duration.ofMinutes(30))
        );

        // 2. SimpleMailMessage가 아닌 MimeMessage로 발송됐는지 확인
        //    → HTML 이메일을 쓰려면 MimeMessage여야 함
        then(mailSender).should().send(any(MimeMessage.class));
    }

    // ===================== verifyToken =====================

    @Test
    @DisplayName("토큰 검증 성공 - 이메일 반환 및 토큰 즉시 삭제")
    void 토큰_검증_성공() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email_verify:valid-token"))
                .willReturn("test@email.com");

        // when
        String result = emailVerificationService.verifyToken("valid-token");

        // then
        assertThat(result).isEqualTo("test@email.com");

        // 인증 완료된 토큰은 즉시 삭제되어야 재사용이 불가능함
        then(redisTemplate).should().delete("email_verify:valid-token");
    }

    @Test
    @DisplayName("토큰 검증 실패 - 만료된 토큰은 null 반환 및 delete 미호출")
    void 토큰_만료_null_반환() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // Redis에 키가 없거나 TTL이 만료된 상황 → null 반환
        given(valueOperations.get(anyString())).willReturn(null);

        // when
        String result = emailVerificationService.verifyToken("expired-token");

        // then
        assertThat(result).isNull();

        // null이면 삭제할 것이 없으므로 delete()는 호출되면 안 됨
        then(redisTemplate).should(never()).delete(anyString());
    }

    // ===================== resendVerificationEmail =====================

    @Test
    @DisplayName("이메일 재발송 - 쿨타임(60초) 내 재요청 시 TOO_MANY_REQUESTS 예외")
    void 재발송_쿨타임_내_요청_시_TOO_MANY_REQUESTS_예외() {
        // given
        // resend_cooltime 키가 Redis에 존재 → 60초 이내 재요청 상태
        given(redisTemplate.hasKey("resend_cooltime:test@email.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.resendVerificationEmail("test@email.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);

        // 차단됐으므로 메일 발송이 일어나면 안 됨
        then(mailSender).should(never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("이메일 재발송 성공 - 쿨타임 지난 후 새 토큰 발송 및 쿨타임 키 저장")
    void 재발송_성공_시_쿨타임키_저장() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        // resend_cooltime 키 없음 → 재발송 가능한 상태
        given(redisTemplate.hasKey("resend_cooltime:test@email.com")).willReturn(false);

        // when
        emailVerificationService.resendVerificationEmail("test@email.com");

        // then
        // 1. 쿨타임 키가 60초 TTL로 저장됐는지 확인
        //    → 이후 60초간 재요청 차단
        then(valueOperations).should().set(
                eq("resend_cooltime:test@email.com"),
                eq("1"),
                eq(Duration.ofSeconds(60))
        );

        // 2. 메일이 실제로 발송됐는지 확인
        then(mailSender).should().send(any(MimeMessage.class));
    }
}
