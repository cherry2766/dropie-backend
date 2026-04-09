package com.dropie.service.tag;

import com.dropie.domain.tag.Tag;
import com.dropie.dto.response.tag.TagResponse;
import com.dropie.repository.tag.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import java.util.List;

// Spring Context 없이 Mockito만으로 TagService 단위 테스트
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    // 테스트 대상 — @Mock으로 선언된 TagRepository가 자동 주입됨
    @InjectMocks
    private TagService tagService;

    // 실제 DB 대신 가짜 객체로 대체
    @Mock
    private TagRepository tagRepository;

    @Test
    @DisplayName("태그 목록 조회 성공")
    void 태그_목록_조회_성공() {
        // given
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("달콤한").build(),
                Tag.builder().id(2L).name("바삭한").build()
        );

        given(tagRepository.findAll()).willReturn(tags);

        // when
        List<TagResponse> result = tagService.getTags();

        // then
        assertThat(result).hasSize(2);
        // DB엔 "달콤한", 응답엔 "#달콤한"으로 가공되는지 확인
        assertThat(result.get(0).getName()).isEqualTo("#달콤한");
        assertThat(result.get(1).getName()).isEqualTo("#바삭한");
    }

}