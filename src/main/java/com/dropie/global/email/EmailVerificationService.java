package com.dropie.global.email;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    // Redis 키 형식: email_verify:{UUID토큰}
    private static final String KEY_PREFIX = "email_verify:";
    // 인증 링크 유효 시간: 30분
    private static final Duration TTL = Duration.ofMinutes(30);

    // 인증 이메일 발송
    // → 회원가입 완료 후 AuthService에서 호출
    @Async
    public void sendVerificationEmail(String email) {
        // UUID : 충돌 가능성이 극히 낮은 무작위 고유 식별자
        // → 예측 불가능해서 토큰으로 사용하기 적합
        String token = UUID.randomUUID().toString();

        // Redis에 토큰 → 이메일 매핑 저장 (30분 후 자동 만료)
        redisTemplate.opsForValue().set(KEY_PREFIX + token, email, TTL);

        // 인증 이메일 발송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[Dropie] 이메일 인증을 완료해주세요");
        message.setText(
                "아래 링크를 클릭하면 이메일 인증이 완료됩니다.\n\n" +
                        "http://localhost:8080/auth/verify-email?token=" + token + "\n\n" +
                        "링크는 30분간 유효합니다."
        );
        mailSender.send(message);
    }

    // 토큰 검증 후 해당 이메일 반환
    // → 토큰이 없거나 만료됐으면 null 반환
    public String verifyToken(String token) {
        String email = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (email != null) {
            // 인증 완료된 토큰은 즉시 삭제 (재사용 방지)
            redisTemplate.delete(KEY_PREFIX + token);
        }
        return email;
    }
}
