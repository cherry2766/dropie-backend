package com.dropie.dto.response.tag;

import com.dropie.domain.tag.Tag;
import lombok.Getter;

// GET /tags 응답 DTO
// { "id": 1, "name": "#달콤한" }
@Getter
public class TagResponse {

    private final Long id;
    private final String name;

    public TagResponse(Tag tag) {
        this.id = tag.getId();
        this.name = "#" + tag.getName(); // DB엔 "달콤한", 응답엔 "#달콤한"
    }
}
