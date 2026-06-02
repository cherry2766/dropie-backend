package com.dropie.global.s3;

import com.dropie.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/images")
public class S3Controller {

    private final S3Service s3Service;

    /**
     * 관리자 전용 이미지 업로드용 Presigned URL 발급
     * 프론트는 이 URL을 받아서 S3에 직접 이미지를 업로드하고,
     * 응답으로 받은 imageUrl을 이벤트/상품 등록 API에 포함해서 보냄
     * POST /admin/images/presigned-url
     */
    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @RequestBody @Valid PresignedUrlRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("Presigned URL 요청: userId={}, fileName={}, contentType={}",
                userDetails.getUser().getId(), request.getFileName(), request.getContentType());

        PresignedUrlResponse response = s3Service.generatePresignedUrl(
                request.getFileName(),
                request.getContentType()
        );
        return ResponseEntity.ok(response);
    }
}
