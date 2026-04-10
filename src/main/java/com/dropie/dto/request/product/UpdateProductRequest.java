package com.dropie.dto.request.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
