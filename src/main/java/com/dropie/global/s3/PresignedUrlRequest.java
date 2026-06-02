package com.dropie.global.s3;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PresignedUrlRequest {

    @NotBlank(message = "파일명은 필수입니다.")
    private String fileName;    // 업로드할 파일 이름 (예: "product-thumbnail.jpg")

    @NotBlank(message = "파일 타입은 필수입니다.")
    private String contentType; // 파일 MIME 타입 (예: "image/jpeg", "image/png")
}
