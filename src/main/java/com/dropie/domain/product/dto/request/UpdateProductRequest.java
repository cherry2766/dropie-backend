package com.dropie.domain.product.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// PATCH 요청 DTO — 변경할 필드만, 모두 null 허용
// price는 null 체크를 위해 Integer(wrapper) 사용
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private String name;
    private String imageUrl;
    private String description;
    private Integer price;

    // PATCH 의미:
    //   - null:    변경 없음 (기존 태그 유지)
    //   - []:      모두 제거
    //   - 값 있음: 그 목록으로 통째로 교체 (replace)
    private List<String> tagNames;
}
