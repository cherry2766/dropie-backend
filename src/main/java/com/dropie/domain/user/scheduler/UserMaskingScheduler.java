package com.dropie.domain.user.scheduler;

import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 탈퇴 후 30일이 지난 유저의 이메일을 일괄 마스킹하는 배치
// → 매일 자정에 자동 실행 (cron: "초 분 시 일 월 요일")
// → 닉네임은 탈퇴 시점에 이미 마스킹됐으므로 이 배치는 이메일만 처리
// → @Component로 등록 + DropieApplication의 @EnableScheduling으로 활성화
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMaskingScheduler {

    private final UserRepository userRepository;

    // 마스킹 유예 기간 — 정책 변경 시 한 곳만 수정하도록 상수화
    private static final int RETENTION_DAYS = 30;

    // 이미 마스킹된 row를 다시 처리하지 않기 위한 식별 prefix
    private static final String MASKED_EMAIL_PREFIX = "withdrawn_";

    // 매일 자정 실행
    // → cron 표현식: "초 분 시 일 월 요일"
    // → "0 0 0 * * *" = 매일 00:00:00
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void maskWithdrawnUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        log.info("[maskWithdrawnUsers] 배치 시작 - threshold: {}", threshold);

        // deletedAt이 threshold 이전이고, 아직 이메일 마스킹이 안 된 유저 조회
        List<User> targets = userRepository
                .findByDeletedAtBeforeAndEmailNotStartingWith(threshold, MASKED_EMAIL_PREFIX);

        if (targets.isEmpty()) {
            log.info("[maskWithdrawnUsers] 마스킹 대상 없음");
            return;
        }

        // 영속 상태이므로 메서드 호출만으로 dirty checking에 의해 UPDATE 발생
        // → 별도 save() 호출 불필요
        targets.forEach(User::maskPersonalInfo);

        log.info("[maskWithdrawnUsers] 마스킹 완료 - 대상 수: {}", targets.size());
    }
}
