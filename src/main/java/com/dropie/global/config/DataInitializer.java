package com.dropie.global.config;

import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 실행 시 초기 데이터를 DB에 심어주는 클래스
 *
 * ApplicationRunner를 구현하면 Spring Boot가 완전히 뜬 직후 run() 메서드를 자동으로 호출함
 * → JPA, Security 등 모든 빈이 준비된 상태에서 실행되므로 안전하게 DB 작업 가능
 *
 * 관리자 회원가입 API를 외부에 열면 누구나 관리자가 될 수 있어 위험
 * → 앱 실행 시 한 번만 자동으로 만들고, 이미 있으면 건너뜀
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.nickname}")
    private String adminNickname;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        createAdminIfNotExists();
        createTagsIfNotExists();
    }

    /**
     * 관리자 계정이 없을 때만 생성
     * <p>
     * 매번 실행하면 서버 재시작 시마다 중복 생성 시도 → 이미 있으면 건너뜀
     * existsByEmail()로 먼저 확인해서 중복 가입 방지
     */
    private void createAdminIfNotExists() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("[DataInitializer] 관리자 계정 이미 존재 - 생성 건너뜀 (email: {})", adminEmail);
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .nickname(adminNickname)
                .role(Role.ADMIN)
                .build();

        // 관리자 계정은 이메일 인증 링크 없이 생성 시 바로 인증 완료 상태로 설정
        admin.verifyEmail();
        userRepository.save(admin);
        log.info("[DataInitializer] 관리자 계정 생성 완료 (email: {})", adminEmail);
    }

    // 태그가 하나도 없을 때만 삽입 — List.of()로 순서 보장 → ID 1~10 순서대로 할당됨
    private void createTagsIfNotExists() {
        if (tagRepository.count() > 0) {
            log.info("[DataInitializer] 태그 이미 존재 - 생성 건너뜀");
            return;
        }

        List<String> tagNames = List.of(
                "달콤한", "바삭한", "크리미한", "과일", "초콜릿",
                "녹차", "빵류", "도넛류", "고소한", "시즌한정"
        );

        tagNames.forEach(name -> tagRepository.save(Tag.builder().name(name).build()));
        log.info("[DataInitializer] 태그 {}개 생성 완료", tagNames.size());
    }
}
