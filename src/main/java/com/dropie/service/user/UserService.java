package com.dropie.service.user;

import com.dropie.domain.user.User;
import com.dropie.dto.response.user.UserResponse;
import com.dropie.exception.BusinessException;
import com.dropie.exception.ErrorCode;
import com.dropie.repository.user.UserRepository;
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
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        log.info("[getMe] 조회 완료 - userId: {}", user.getId());

        // User 엔티티를 그대로 반환하지 않고 DTO로 변환해서 반환
        // → 필요한 필드만 노출, password 같은 민감한 정보 차단
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole().name()   // enum → String 변환 (ex. "USER", "ADMIN")
        );
    }


}
