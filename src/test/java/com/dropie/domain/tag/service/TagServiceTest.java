package com.dropie.domain.tag.service;

import com.dropie.domain.tag.dto.response.TagResponse;
import com.dropie.domain.tag.entity.Tag;
import com.dropie.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

// Spring Context 없이 Mockito만으로 TagService 단위 테스트
//
// TagService는 두 개의 책임을 가진다:
//   1) getOnboardingTags()      — 회원가입 화면용. onboardingExposed=true만 반환
//   2) searchForAdmin(keyword)  — 어드민 자동완성. 부분 일치 + 빈 입력 빈 배열
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    // 테스트 대상 — @Mock으로 선언된 TagRepository가 자동 주입됨
    @InjectMocks
    private TagService tagService;

    // 실제 DB 대신 가짜 객체로 대체
    @Mock
    private TagRepository tagRepository;

    // ===== getOnboardingTags() =====

    @Test
    @DisplayName("회원가입용 태그 조회 성공 — onboardingExposed=true 만 반환되고 #prefix 변환됨")
    void 회원가입_태그_조회_성공() {
        // given
        // Repository는 onboardingExposed=true 인 태그만 골라서 반환한다고 가정
        // (어드민이 자동 생성한 false 태그는 이미 걸러진 상태)
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("달콤한").build(),
                Tag.builder().id(2L).name("바삭한").build()
        );

        given(tagRepository.findAllByOnboardingExposedTrue()).willReturn(tags);

        // when
        List<TagResponse> result = tagService.getOnboardingTags();

        // then
        assertThat(result).hasSize(2);
        // DB엔 "달콤한", 응답엔 "#달콤한"으로 가공되는지 확인
        assertThat(result.get(0).getName()).isEqualTo("#달콤한");
        assertThat(result.get(1).getName()).isEqualTo("#바삭한");
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("회원가입용 태그 조회 — 노출 가능한 태그가 0개면 빈 리스트")
    void 회원가입_태그_없으면_빈리스트() {
        // given
        // 회원가입 노출 태그가 모두 비활성화된 엣지 상황
        given(tagRepository.findAllByOnboardingExposedTrue()).willReturn(List.of());

        // when
        List<TagResponse> result = tagService.getOnboardingTags();

        // then
        assertThat(result).isEmpty();
    }

    // ===== searchForAdmin(keyword) =====

    @Test
    @DisplayName("어드민 자동완성 — keyword가 부분 일치하는 태그 반환")
    void 어드민_자동완성_정상() {
        // given
        // "초"를 포함한 태그가 두 개 있다고 가정
        List<Tag> tags = List.of(
                Tag.builder().id(5L).name("초콜릿").build(),
                Tag.builder().id(11L).name("초코칩").build()
        );

        given(tagRepository.findTop20ByNameContaining("초")).willReturn(tags);

        // when
        List<TagResponse> result = tagService.searchForAdmin("초");

        // then
        assertThat(result).hasSize(2);
        // 자동완성 응답도 #prefix 변환을 거쳐 일관된 표기로 노출됨
        assertThat(result.get(0).getName()).isEqualTo("#초콜릿");
        assertThat(result.get(1).getName()).isEqualTo("#초코칩");
    }

    @Test
    @DisplayName("어드민 자동완성 — keyword 앞뒤 공백은 trim 후 검색")
    void 어드민_자동완성_공백_trim() {
        // given
        // 사용자가 "  초  "처럼 입력해도 백엔드에서 trim 후 "초"로 검색
        // → DB 호출은 정확히 "초"로 일어나야 함
        List<Tag> tags = List.of(
                Tag.builder().id(5L).name("초콜릿").build()
        );

        given(tagRepository.findTop20ByNameContaining("초")).willReturn(tags);

        // when
        List<TagResponse> result = tagService.searchForAdmin("  초  ");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("#초콜릿");
        // trim된 정확한 값으로 호출됐는지 확인 (양 옆에 공백이 끼면 매칭이 빗나감)
        then(tagRepository).should().findTop20ByNameContaining("초");
    }

    @Test
    @DisplayName("어드민 자동완성 — keyword가 빈 문자열이면 DB 호출 없이 빈 리스트")
    void 어드민_자동완성_빈문자열() {
        // when
        // 사용자가 글자를 안 친 상태(또는 입력값을 다 지운 상태) → 추천 노출 X
        List<TagResponse> result = tagService.searchForAdmin("");

        // then
        assertThat(result).isEmpty();
        // 빈 입력엔 절대 DB까지 가지 않아야 함 (불필요한 부하 방지)
        then(tagRepository).should(never()).findTop20ByNameContaining(any());
    }

    @Test
    @DisplayName("어드민 자동완성 — keyword가 null이면 DB 호출 없이 빈 리스트")
    void 어드민_자동완성_null() {
        // when
        // null 방어 — 컨트롤러 default 값이 누락되거나 외부 호출에서 null이 들어와도 안전
        List<TagResponse> result = tagService.searchForAdmin(null);

        // then
        assertThat(result).isEmpty();
        then(tagRepository).should(never()).findTop20ByNameContaining(any());
    }

    @Test
    @DisplayName("어드민 자동완성 — keyword가 공백만 있으면 DB 호출 없이 빈 리스트")
    void 어드민_자동완성_공백만() {
        // when
        // "   "처럼 공백만 입력한 경우도 의미 없는 검색이라 빈 배열 반환 정책
        List<TagResponse> result = tagService.searchForAdmin("   ");

        // then
        assertThat(result).isEmpty();
        then(tagRepository).should(never()).findTop20ByNameContaining(any());
    }

    @Test
    @DisplayName("어드민 자동완성 — 매칭 결과가 0개면 빈 리스트")
    void 어드민_자동완성_매칭_없음() {
        // given
        // 키워드를 입력했지만 매칭되는 태그가 없는 엣지
        given(tagRepository.findTop20ByNameContaining("없는키워드")).willReturn(List.of());

        // when
        List<TagResponse> result = tagService.searchForAdmin("없는키워드");

        // then
        assertThat(result).isEmpty();
        // 정상 호출 케이스이므로 DB 호출은 일어나야 함 (위 빈 입력 케이스와 구분)
        then(tagRepository).should().findTop20ByNameContaining("없는키워드");
    }
}
