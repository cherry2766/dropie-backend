package com.dropie.domain.user.service;

import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

// Spring Context를 띄우지 않고 Mockito만으로 서비스 로직 단위 테스트
// → 빠르고 DB/외부 의존성 없이 순수 비즈니스 로직만 검증
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // 테스트 대상 - @Mock들이 자동 주입됨
    @InjectMocks
    private UserService userService;

    // @Mock : 실제 DB 대신 가짜 객체로 대체
    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("내 정보 조회 성공")
    void 내정보_조회_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getMe("test@email.com");

        // then
        assertThat(response.getEmail()).isEqualTo("test@email.com");
        assertThat(response.getNickname()).isEqualTo("체리");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 유저 없음")
    void 내정보_조회_유저없음_예외() {
        // given
        // Optional.empty() → findByEmail이 아무것도 못 찾은 상황
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMe("ghost@email.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
