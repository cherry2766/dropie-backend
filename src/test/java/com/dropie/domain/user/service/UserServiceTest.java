package com.dropie.domain.user.service;

import com.dropie.domain.preference.repository.UserPreferenceRepository;
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

    @Mock
    private UserPreferenceRepository preferenceRepository;

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

    // ===================== skipOnboarding =====================

    @Test
    @DisplayName("온보딩 스킵 성공 - onboardingSkipped가 true로 변경됨")
    void 온보딩_스킵_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // 취향 없음 + 스킵 안 한 상태 → 스킵 가능
        given(preferenceRepository.existsByUser(user)).willReturn(false);

        // when
        userService.skipOnboarding("test@email.com");

        // then
        assertThat(user.isOnboardingSkipped()).isTrue();
    }

    @Test
    @DisplayName("온보딩 스킵 - 이미 스킵한 유저 재요청 시 무시 (멱등성)")
    void 온보딩_스킵_이미스킵한유저_무시() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();
        // 이미 스킵된 상태 → isOnboardingSkipped() = true → 단락 평가로 preferenceRepository 미호출
        user.skipOnboarding();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when & then - 예외 없이 조용히 종료
        assertThatCode(() -> userService.skipOnboarding("test@email.com"))
                .doesNotThrowAnyException();

        // onboardingSkipped는 여전히 true (중복 호출해도 상태 변화 없음)
        assertThat(user.isOnboardingSkipped()).isTrue();
    }

    @Test
    @DisplayName("온보딩 스킵 - 이미 취향 있는 유저 요청 시 무시")
    void 온보딩_스킵_취향있는유저_무시() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // 취향이 이미 있는 상태 → 어차피 showOnboarding = false이므로 스킵 불필요
        given(preferenceRepository.existsByUser(user)).willReturn(true);

        // when
        userService.skipOnboarding("test@email.com");

        // then - 스킵 플래그 변경 없어야 함
        assertThat(user.isOnboardingSkipped()).isFalse();
    }

    @Test
    @DisplayName("온보딩 스킵 실패 - 존재하지 않는 이메일이면 UserNotFoundException 예외")
    void 온보딩_스킵_유저없음_예외() {
        // given
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.skipOnboarding("ghost@email.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
