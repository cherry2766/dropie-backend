package com.dropie.domain.tag.dto.response;

import com.dropie.domain.tag.entity.Tag;
import lombok.Builder;
import lombok.Getter;

// GET /tags 응답 DTO
// { "id": 1, "name": "#달콤한" }
@Getter
@Builder
public class TagResponse {

    private Long id;
    private String name;

    // Entity → DTO 변환 팩토리 메서드
    // DB엔 "달콤한", 응답엔 "#달콤한"으로 가공
    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name("#" + tag.getName())
                .build();
    }
}
