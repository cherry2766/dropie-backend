package com.dropie.domain.user.service;

import com.dropie.domain.auth.entity.RefreshToken;
import com.dropie.domain.auth.repository.RefreshTokenRepository;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
import com.dropie.domain.user.dto.request.UpdateNicknameRequest;
import com.dropie.domain.user.dto.request.UpdateProfileImageRequest;
import com.dropie.domain.user.dto.response.UserResponse;
import com.dropie.domain.user.entity.Role;
import com.dropie.domain.user.entity.User;
import com.dropie.domain.user.repository.UserRepository;
import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import com.dropie.global.exception.custom.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

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

    // ===================== withdraw =====================

    @Test
    @DisplayName("회원 탈퇴 성공 - deletedAt 설정되고 RefreshToken 삭제됨")
    void 회원_탈퇴_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        // RefreshToken은 내부 상태를 검사할 필요 없으므로 mock으로 대체
        RefreshToken refreshToken = mock(RefreshToken.class);

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUser(user)).willReturn(Optional.of(refreshToken));

        // when
        userService.withdraw("test@email.com");

        // then
        // deletedAt이 null이 아니어야 함 (소프트 딜리트 확인)
        assertThat(user.getDeletedAt()).isNotNull();
        // Refresh Token 삭제가 호출됐는지 확인
        then(refreshTokenRepository).should().delete(refreshToken);
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 존재하지 않는 유저면 UserNotFoundException")
    void 회원_탈퇴_유저없음_예외() {
        // given
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.withdraw("ghost@email.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("회원 탈퇴 - 이미 탈퇴한 유저 재요청 시 무시 (멱등성 보장)")
    void 회원_탈퇴_이미탈퇴한유저_무시() {
        // given: 이미 탈퇴 처리된 유저 (withdraw() 호출로 deletedAt 설정)
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();
        user.withdraw(); // deletedAt 세팅

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when - 예외 없이 조용히 종료되어야 함
        assertThatCode(() -> userService.withdraw("test@email.com"))
                .doesNotThrowAnyException();

        // then - RT 삭제 호출 안 됨 (이미 탈퇴 처리된 유저이므로)
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원 탈퇴 - RefreshToken이 없어도 정상 처리 (RT 없이 탈퇴 가능)")
    void 회원_탈퇴_RT없어도_성공() {
        // given: RT가 없는 유저 (ex. 자동 만료로 이미 삭제된 상태)
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUser(user)).willReturn(Optional.empty());

        // when
        userService.withdraw("test@email.com");

        // then
        assertThat(user.getDeletedAt()).isNotNull();
        // Optional.empty()이므로 delete()는 호출되지 않아야 함
        then(refreshTokenRepository).should(never()).delete(any());
    }

    // ===================== updateNickname =====================

    @Test
    @DisplayName("닉네임 수정 성공")
    void 닉네임_수정_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndDeletedAtIsNull("딸기")).willReturn(false);

        // when
        UserResponse response = userService.updateNickname("test@email.com", new UpdateNicknameRequest("딸기"));

        // then
        assertThat(response.getNickname()).isEqualTo("딸기");
        assertThat(user.getNickname()).isEqualTo("딸기"); // 엔티티 상태도 변경됐는지 확인
    }

    @Test
    @DisplayName("닉네임 수정 성공 - 현재 닉네임과 동일하면 중복체크 없이 통과")
    void 닉네임_수정_동일닉네임_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.updateNickname("test@email.com", new UpdateNicknameRequest("체리"));

        // then
        assertThat(response.getNickname()).isEqualTo("체리");
        // 본인 닉네임과 같으면 existsByNickname 호출하지 않아야 함
        then(userRepository).should(never()).existsByNicknameAndDeletedAtIsNull(anyString());
    }

    @Test
    @DisplayName("닉네임 수정 실패 - 이미 사용 중인 닉네임이면 DUPLICATE_NICKNAME 예외")
    void 닉네임_수정_중복_예외() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndDeletedAtIsNull("딸기")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateNickname("test@email.com", new UpdateNicknameRequest("딸기")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("닉네임 수정 실패 - 존재하지 않는 유저")
    void 닉네임_수정_유저없음_예외() {
        // given
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateNickname("ghost@email.com", new UpdateNicknameRequest("딸기")))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ===================== updateProfileImage =====================

    @Test
    @DisplayName("프로필 이미지 수정 성공")
    void 프로필이미지_수정_성공() {
        // given
        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.updateProfileImage("test@email.com",
                new UpdateProfileImageRequest("https://s3.amazonaws.com/profiles/new.jpg"));

        // then
        assertThat(response.getProfileImageUrl()).isEqualTo("https://s3.amazonaws.com/profiles/new.jpg");
    }

    @Test
    @DisplayName("프로필 이미지 수정 실패 - 존재하지 않는 유저")
    void 프로필이미지_수정_유저없음_예외() {
        // given
        given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateProfileImage("ghost@email.com",
                new UpdateProfileImageRequest("https://s3.amazonaws.com/profiles/new.jpg")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("회원 탈퇴 - deletedAt 기록 + 닉네임 즉시 마스킹")
    void 회원_탈퇴_닉네임_즉시_마스킹() throws Exception {
        User user = User.builder()
                .email("test@email.com")
                .password("pw")
                .nickname("체리")
                .role(Role.USER)
                .build();
        // id는 @GeneratedValue라 빌더로 못 넣음 → 리플렉션으로 강제 주입
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 42L);
        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        userService.withdraw("test@email.com");

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getNickname()).isEqualTo("탈퇴회원_42");
        // 이메일은 아직 그대로 (30일 후 스케줄러가 마스킹)
        assertThat(user.getEmail()).isEqualTo("test@email.com");
    }
}
