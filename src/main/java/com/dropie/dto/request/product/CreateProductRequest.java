package com.dropie.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
