package com.dropie.global.email;

import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // Redis 키 형식: pwd_reset:{UUID토큰} → 이메일
    // EmailVerificationService의 "email_verify:" 와 구분하기 위해 별도 prefix 사용
    private static final String KEY_PREFIX = "pwd_reset:";

    // 재설정 링크 유효 시간: 30분
    private static final Duration TTL = Duration.ofMinutes(30);

    // 비밀번호 재설정 이메일 발송 요청 처리 (POST /auth/password-reset/request)
    // → 이메일이 존재하지 않아도 동일한 성공 응답을 반환하는 이유:
    //   공격자가 특정 이메일의 가입 여부를 알 수 없도록 정보 노출을 방지
    @Transactional(readOnly = true)
    public void requestPasswordReset(String email) {
        // ifPresent: 유저가 있을 때만 메일 발송, 없으면 아무것도 하지 않고 정상 반환
        userRepository.findByEmail(email)
                .ifPresent(user -> sendResetEmail(email));
    }

    // 비동기로 재설정 메일 발송
    // → @Async: 메일 발송을 별도 스레드에서 실행해 API 응답 속도에 영향 없음
    @Async
    public void sendResetEmail(String email) {
        String token = UUID.randomUUID().toString();

        // Redis에 "pwd_reset:{토큰}" → 이메일 저장 (30분 후 자동 만료)
        redisTemplate.opsForValue().set(KEY_PREFIX + token, email, TTL);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // false: 첨부파일 없음 / UTF-8: 한글 깨짐 방지
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, "Dropie"));
            helper.setTo(email);
            helper.setSubject("[Dropie] 비밀번호 재설정 링크입니다");

            // 링크를 백엔드가 아닌 프론트 URL로 직접 연결하는 이유:
            // → 이메일 인증과 달리 비밀번호 재설정은 백엔드에서 리다이렉트할 이유가 없음
            // → 프론트가 URL의 token 파라미터를 꺼내서 confirm API를 직접 호출하는 구조가 더 깔끔함
            String resetUrl = "http://localhost:5173/reset-password?token=" + token;
            String htmlContent = """
                    <p>비밀번호 재설정을 요청하셨습니다.</p>
                    <p>아래 버튼을 클릭해 새 비밀번호를 설정하세요.</p>
                    <p>
                        <a href="%s">비밀번호 재설정하기</a>
                    </p>
                    <p>링크는 30분간 유효합니다.</p>
                    <p>본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                    """.formatted(resetUrl);

            // true: HTML 모드 활성화 — <a> 태그를 클릭 가능한 링크로 렌더링
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[sendResetEmail] 비밀번호 재설정 메일 발송 완료 - to: {}", email);

        } catch (Exception e) {
            // @Async 내부 예외는 호출부로 전파되지 않음 → 로그만 남기고 넘어감
            log.error("[sendResetEmail] 메일 발송 실패 - to: {}, error: {}", email, e.getMessage());
        }
    }

    // 토큰 검증 후 비밀번호 변경 (POST /auth/password-reset/confirm)
    @Transactional
    public void resetPassword(String token, String newPassword) {
        // Redis에서 토큰으로 이메일 조회
        // → 토큰이 없거나 이미 만료됐으면 null 반환
        String email = redisTemplate.opsForValue().get(KEY_PREFIX + token);

        if (email == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 새 비밀번호를 암호화해서 변경
        user.changePassword(passwordEncoder.encode(newPassword));

        // 사용한 토큰 즉시 삭제 (같은 링크로 비밀번호를 여러 번 바꾸는 것을 방지)
        redisTemplate.delete(KEY_PREFIX + token);

        log.info("[resetPassword] 비밀번호 변경 완료 - email: {}", email);
    }
}
