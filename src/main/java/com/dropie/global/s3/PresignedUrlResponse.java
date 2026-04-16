package com.dropie.global.s3.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresignedUrlResponse {

    private String presignedUrl; // 프론트가 이미지를 PUT 업로드할 임시 URL (10분 유효)
    private String imageUrl;     // 업로드 완료 후 DB에 저장할 최종 S3 이미지 URL
}
