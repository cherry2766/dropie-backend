package com.dropie.domain.user.service;

import com.dropie.domain.auth.repository.RefreshTokenRepository;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.global.exception.custom.UserNotFoundException;
import com.dropie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserResponse getMe(String email) {
        log.debug("[getMe] 조회 요청 - email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[getMe] 유저 없음 - email: {}", email);
                    return new UserNotFoundException();
                });

        log.info("[getMe] 조회 완료 - userId: {}", user.getId());

        // User 엔티티를 그대로 반환하지 않고 DTO로 변환해서 반환
        // → 필요한 필드만 노출, password 같은 민감한 정보 차단
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole().name())  // enum → String 변환 (ex. "USER", "ADMIN")
                .build();
    }

    @Transactional
    public void skipOnboarding(String email) {
        log.debug("[skipOnboarding] 온보딩 스킵 요청 - email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        // 이미 스킵했거나 취향이 있으면 무시 (멱등성 보장)
        if (user.isOnboardingSkipped() || preferenceRepository.existsByUser(user)) {
            return;
        }

        user.skipOnboarding();
        log.info("[skipOnboarding] 온보딩 스킵 완료 - email: {}", email);
    }

    // 회원 탈퇴
    // @Transactional : 유저 상태 변경 + RT 삭제가 하나의 트랜잭션으로 묶여야 함
    @Transactional
    public void withdraw(String email) {
        log.debug("[withdraw] 탈퇴 요청 - email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        // 이미 탈퇴한 유저가 다시 요청한 경우 무시
        // → 네트워크 재시도나 중복 클릭에도 안전하게 처리됨
        if (user.getDeletedAt() != null) {
            log.debug("[withdraw] 이미 탈퇴 처리된 유저 - email: {}", email);
            return;
        }

        // 소프트 딜리트: deletedAt에 현재 시간 기록
        user.withdraw();

        // Refresh Token 즉시 삭제
        // → 탈퇴 직후 기존 RT로 토큰 재발급 시도해도 INVALID_TOKEN 처리됨
        refreshTokenRepository.findByUser(user)
                .ifPresent(refreshTokenRepository::delete);

        log.info("[withdraw] 탈퇴 완료 - email: {}", email);
    }


}
