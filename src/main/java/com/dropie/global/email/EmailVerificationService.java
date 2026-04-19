package com.dropie.global.email;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    // application.yml의 spring.mail.username 값을 주입
    // → Gmail 발신 계정 주소 (환경변수로 관리됨)
    @Value("${spring.mail.username}")
    private String fromEmail;
    // Redis 키 형식: email_verify:{UUID토큰}
    private static final String KEY_PREFIX = "email_verify:";
    // 인증 링크 유효 시간: 30분
    private static final Duration TTL = Duration.ofMinutes(30);

    private static final String COOLTIME_PREFIX = "resend_cooltime:";
    private static final Duration COOLTIME = Duration.ofSeconds(60);

    @Async
    public void sendVerificationEmail(String email) {
        // UUID : 충돌 가능성이 극히 낮은 무작위 고유 식별자
        // → 예측 불가능해서 토큰으로 사용하기 적합
        String token = UUID.randomUUID().toString();

        // Redis에 토큰 → 이메일 매핑 저장 (30분 후 자동 만료)
        redisTemplate.opsForValue().set(KEY_PREFIX + token, email, TTL);

        try {
            // SimpleMailMessage → MimeMessage로 전환한 이유:
            // SimpleMailMessage는 발신자 표시 이름(Display Name) 설정을 지원하지 않음
            // MimeMessage + MimeMessageHelper를 쓰면 "Dropie <주소>" 형태로 설정 가능
            MimeMessage message = mailSender.createMimeMessage();

            // false: 첨부파일 없음 / "UTF-8": 한글 제목/본문 깨짐 방지
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            // 발신자 이름을 "Dropie"로 설정
            // → 메일 수신함에서 "Dropie"로 표시됨 (이메일 주소 대신)
            helper.setFrom(new InternetAddress(fromEmail, "Dropie"));
            helper.setTo(email);
            helper.setSubject("[Dropie] 이메일 인증을 완료해주세요");
            // true: HTML 이메일로 전송
            // → plain text는 localhost URL을 클릭 가능한 링크로 변환하지 않음
            // → <a href> 태그를 써야 어떤 메일 클라이언트에서도 클릭 가능
            String verifyUrl = "http://localhost:8080/auth/verify-email?token=" + token;
            String htmlContent = """
                    <p>아래 버튼을 클릭하면 이메일 인증이 완료됩니다.</p>
                    <p>
                        <a href="%s">이메일 인증하기</a>
                    </p>
                    <p>링크는 30분간 유효합니다.</p>
                    """.formatted(verifyUrl);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("[sendVerificationEmail] 인증 메일 발송 완료 - to: {}", email);

        } catch (Exception e) {
            // @Async 메서드 내부 예외는 호출부로 전파되지 않음
            // → 메일 발송 실패해도 회원가입 트랜잭션에 영향 없음
            // → 로그만 남기고 넘어감 (재발송은 별도 기능으로 구현)
            log.error("[sendVerificationEmail] 메일 발송 실패 - to: {}, error: {}", email, e.getMessage());
        }
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

    // 이메일 인증 재발송
    // → 60초 쿨타임 체크 후 기존 토큰 덮어쓰기 방식으로 새 메일 발송
    public void resendVerificationEmail(String email) {
        String cooltimeKey = COOLTIME_PREFIX + email;

        // 쿨타임 체크 : 60초 이내 재요청이면 차단
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooltimeKey))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        // 기존 sendVerificationEmail() 재사용
        // → 내부에서 새 UUID 토큰 생성 + Redis 덮어쓰기 + 메일 발송
        sendVerificationEmail(email);

        // 쿨타임 설정 (60초 동안 재발송 불가)
        redisTemplate.opsForValue().set(cooltimeKey, "1", COOLTIME);
    }
}
