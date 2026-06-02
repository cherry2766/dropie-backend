package com.dropie.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// POST 요청 DTO
// price, stock은 int(primitive)로 선언하면 @NotNull이 동작 안 함
// → null 체크가 필요한 필드는 Integer(wrapper)로 선언
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotNull
    private Long eventId;       // 어느 이벤트에 속한 상품인지

    @NotBlank
    private String name;

    private String imageUrl;    // 선택
    private String description; // 선택

    @NotNull
    private Integer price;

    @NotNull
    private Integer stock;

    // 태그 이름 리스트 - 이름으로 받음
    // - 빈 배열/null : 태그 없는 상품
    // - 값 있음 : 백엔드에서 find-or-create로 처리
    private List<String> tagNames;
}
