package com.dropie.global.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("인증 이메일 발송 - Redis에 토큰 저장 및 메일 전송")
    void 인증메일_발송_성공() {
        // when
        emailVerificationService.sendVerificationEmail("test@email.com");

        // then
        // 1. Redis에 토큰이 저장됐는지 확인
        //    토큰이 UUID라 정확한 키를 알 수 없으므로 any()로 검증
        //    Duration.ofMinutes(30) = 30분 TTL이 설정됐는지 확인
        then(valueOperations).should().set(
                anyString(),
                eq("test@email.com"),
                eq(Duration.ofMinutes(30))
        );

        // 2. 메일 발송이 1번 호출됐는지 확인
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("토큰 검증 성공 - 이메일 반환 및 토큰 즉시 삭제")
    void 토큰_검증_성공() {
        // given
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
        // Redis에 키가 없거나 TTL이 만료된 상황 → null 반환
        given(valueOperations.get(anyString())).willReturn(null);

        // when
        String result = emailVerificationService.verifyToken("expired-token");

        // then
        assertThat(result).isNull();

        // null이면 삭제할 것이 없으므로 delete()는 호출되면 안 됨
        then(redisTemplate).should(never()).delete(anyString());
    }
}