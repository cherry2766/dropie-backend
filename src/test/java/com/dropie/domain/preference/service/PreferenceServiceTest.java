package com.dropie.domain.preference.service;

import com.dropie.domain.preference.dto.request.PreferenceRequest;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
import com.dropie.domain.recommendation.service.TasteTagService;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

// Spring Context 없이 Mockito만으로 PreferenceService 단위 테스트
@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    // 테스트 대상 — 아래 @Mock들이 자동 주입됨
    @InjectMocks
    private PreferenceService preferenceService;

    // 실제 DB / 외부 의존 대신 가짜 객체로 대체
    @Mock
    private UserRepository userRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    // 회원가입 태그를 ZSET에 시드 점수로 흘려보내는 의존
    // 추천 시점에 ZSET 한 곳만 보면 4가지 시나리오를 자동 처리하므로 호출 여부를 정확히 검증해야 함
    @Mock
    private TasteTagService tasteTagService;

    @Test
    @DisplayName("취향 태그 등록 성공 — UserPreference 저장 + ZSET 시드 점수 누적")
    void 취향_태그_등록_성공() {
        // given
        PreferenceRequest request = new PreferenceRequest(List.of(1L, 2L, 3L));

        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        // 요청한 tagIds 3개 → 조회된 Tag도 3개 → size 일치 → 정상 케이스
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("달콤한").build(),
                Tag.builder().id(2L).name("바삭한").build(),
                Tag.builder().id(3L).name("크리미한").build()
        );

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(tagRepository.findAllByIdIn(List.of(1L, 2L, 3L))).willReturn(tags);
        // 등록 이력 없는 신규 사용자 → 가드 통과
        given(userPreferenceRepository.existsByUser(user)).willReturn(false);

        // when
        preferenceService.savePreferences("test@email.com", request);

        // then
        // DB에 새 취향 저장 1번
        then(userPreferenceRepository).should().saveAll(any());
        // ZSET 시드 점수 누적 1번 — 회원가입 태그를 추천 흐름에 흘려보냄
        then(tasteTagService).should().addSeedScores(any(), any());
    }

    @Test
    @DisplayName("취향 태그 등록 — tagIds가 비어있으면 DB/ZSET 모두 호출되지 않음 (회원가입에서 태그 건너뜀)")
    void tagIds_비어있으면_저장_스킵() {
        // given
        PreferenceRequest request = new PreferenceRequest(List.of());

        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));

        // when
        preferenceService.savePreferences("test@email.com", request);

        // then
        // 빈 tagIds는 추천 흐름에 시드도 안 들어감 → 추천 시 인기 폴백(시나리오 1)으로 빠지게 됨
        then(tagRepository).shouldHaveNoInteractions();
        then(userPreferenceRepository).shouldHaveNoInteractions();
        then(tasteTagService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취향 태그 등록 실패 — 존재하지 않는 유저")
    void 존재하지_않는_유저_예외() {
        // given
        PreferenceRequest request = new PreferenceRequest(List.of(1L, 2L));

        // Optional.empty() → 해당 이메일 유저가 없는 상황
        given(userRepository.findByEmail("none@email.com")).willReturn(Optional.empty());

        // when & then
        // UserNotFoundException은 BusinessException을 상속하므로 둘 다 검증 가능
        assertThatThrownBy(() -> preferenceService.savePreferences("none@email.com", request))
                .isInstanceOf(UserNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        // 유저 조회에서 막혔으니 그 뒤 어떤 호출도 일어나지 않아야 함
        then(tagRepository).shouldHaveNoInteractions();
        then(userPreferenceRepository).shouldHaveNoInteractions();
        then(tasteTagService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취향 태그 등록 실패 — 존재하지 않는 tagId 포함")
    void 잘못된_태그_아이디_예외() {
        // given
        // tagIds 3개 요청했지만 tagRepository는 2개만 반환 → size 불일치 → TAG_NOT_FOUND 예외
        PreferenceRequest request = new PreferenceRequest(List.of(1L, 2L, 999L));

        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        // 999L은 DB에 없으므로 2개만 반환되는 상황 재현
        given(tagRepository.findAllByIdIn(List.of(1L, 2L, 999L))).willReturn(List.of(
                Tag.builder().id(1L).name("달콤한").build(),
                Tag.builder().id(2L).name("바삭한").build()
        ));

        // when & then
        assertThatThrownBy(() -> preferenceService.savePreferences("test@email.com", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TAG_NOT_FOUND);

        // 태그 검증 실패로 막혔으니 저장/시드 호출이 일어나면 안 됨
        then(userPreferenceRepository).should(never()).saveAll(any());
        then(tasteTagService).should(never()).addSeedScores(any(), any());
    }

    @Test
    @DisplayName("취향 태그 등록 실패 — 이미 등록된 사용자가 다시 호출하면 PREFERENCE_ALREADY_REGISTERED")
    void 이미_등록된_사용자_재등록_예외() {
        // given
        // 회원가입 태그는 한 번만 등록 가능 (이후 수정 불가 정책)
        // → 이미 등록된 사용자가 같은 API를 다시 두드리면 409로 거절
        PreferenceRequest request = new PreferenceRequest(List.of(1L, 2L));

        User user = User.builder()
                .email("test@email.com")
                .password("encoded_pwd")
                .nickname("체리")
                .role(Role.USER)
                .build();

        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("달콤한").build(),
                Tag.builder().id(2L).name("바삭한").build()
        );

        given(userRepository.findByEmail("test@email.com")).willReturn(Optional.of(user));
        given(tagRepository.findAllByIdIn(List.of(1L, 2L))).willReturn(tags);
        // 이미 등록된 상태 → 가드가 발화
        given(userPreferenceRepository.existsByUser(user)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> preferenceService.savePreferences("test@email.com", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PREFERENCE_ALREADY_REGISTERED);

        // 가드에 막혔으니 이후 저장/ZSET 시드 호출이 일어나면 안 됨 (정책 강제)
        then(userPreferenceRepository).should(never()).saveAll(any());
        then(tasteTagService).should(never()).addSeedScores(any(), any());
    }
}
