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

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @InjectMocks
    private TagService tagService;

    @Mock
    private TagRepository tagRepository;

    @Test
    @DisplayName("태그 목록 조회 성공")
    void 태그_목록_조회_성공() {
        // given
        // Tag 엔티티 생성
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("달콤한").build(),
                Tag.builder().id(2L).name("바삭한").build()
        );

        given(tagRepository.findAll()).willReturn(tags);

        // when
        List<TagResponse> result = tagService.getTags();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("#달콤한");
        assertThat(result.get(1).getName()).isEqualTo("#바삭한");
    }

}