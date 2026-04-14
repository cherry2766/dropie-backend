package com.dropie.domain.preference.service;

import com.dropie.domain.preference.dto.request.PreferenceRequest;
import com.dropie.domain.preference.repository.UserPreferenceRepository;
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

// Spring Context 없이 Mockito만으로 PreferenceService 단위 테스트
@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    // 테스트 대상 — 아래 @Mock들이 자동 주입됨
    @InjectMocks
    private PreferenceService preferenceService;

    // 실제 DB 대신 가짜 객체로 대체
    @Mock
    private UserRepository userRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Test
    @DisplayName("취향 태그 등록 성공")
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

        // when
        preferenceService.savePreferences("test@email.com", request);

        // then
        // 기존 취향 삭제가 1번 호출됐는지 확인 (온보딩 덮어쓰기 로직)
        then(userPreferenceRepository).should().deleteByUser(user);
        // 새 취향 저장이 1번 호출됐는지 확인
        then(userPreferenceRepository).should().saveAll(any());
    }

    @Test
    @DisplayName("취향 태그 등록 - tagIds가 비어있으면 저장 없이 종료")
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
        then(tagRepository).shouldHaveNoInteractions();
        then(userPreferenceRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취향 태그 등록 실패 - 존재하지 않는 유저")
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
    }

    @Test
    @DisplayName("취향 태그 등록 실패 - 존재하지 않는 tagId 포함")
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
    }
}
