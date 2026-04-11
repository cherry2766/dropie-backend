package com.dropie.domain.user.service;

import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.global.exception.custom.UserNotFoundException;
import com.dropie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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


}
