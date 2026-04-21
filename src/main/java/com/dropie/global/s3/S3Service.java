package com.dropie.global.s3;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    /**
     * S3 업로드용 Presigned URL과 최종 이미지 URL을 생성해서 반환
     * UUID를 파일명 앞에 붙이는 이유:
     * 같은 이름의 파일을 여러 번 올려도 기존 파일을 덮어쓰지 않게 하기 위함
     * 예: images/a1b2c3d4-product.jpg
     */
    public PresignedUrlResponse generatePresignedUrl(String fileName, String contentType) {
        // 원본 파일명에 한글/공백/특수문자가 포함될 수 있어 확장자만 추출
        // 예: "상품 사진 (1).jpg" → ".jpg" → "images/{UUID}.jpg"
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf("."))
                : "";
        String key = "images/" + UUID.randomUUID() + extension;

        try {
            // S3에 PUT 요청을 허용하는 Presigned URL 생성
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10)) // URL 유효 시간: 10분
                    .putObjectRequest(putObjectRequest)
                    .build();

            // presignedUrl: 프론트가 이미지를 직접 PUT 업로드할 임시 URL
            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            // imageUrl: 업로드 완료 후 실제 이미지에 접근하는 최종 URL (DB에 저장되는 값)
            // 형식: https://{버킷명}.s3.{리전}.amazonaws.com/{key}
            String imageUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

            log.info("Presigned URL 생성 완료: key={}, imageUrl={}", key, imageUrl);

            return new PresignedUrlResponse(presignedUrl, imageUrl);
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: fileName={}, error={}", fileName, e.getMessage());
            throw new BusinessException(ErrorCode.S3_UPLOAD_URL_GENERATION_FAILED);
        }
    }

    /**
     * S3에서 이미지 파일 삭제
     * <p>
     * imageUrl에서 key를 추출하는 이유:
     * S3에 파일을 삭제할 때는 전체 URL이 아니라 버킷 내 경로(key)만 필요
     * 예) https://dropie-bucket.s3.ap-northeast-2.amazonaws.com/images/a1b2-product.jpg
     * → key: images/a1b2-product.jpg
     * <p>
     * 이미지 삭제 실패 시 예외를 던지지 않는 이유:
     * S3 파일이 없어도 DB 삭제는 계속 진행되어야 하기 때문
     * 삭제 실패는 로그로만 기록하고 넘어감
     */
    public void deleteImage(String imageUrl) {
        // imageUrl이 없으면 삭제 시도 X (상품에 이미지가 없을 수도 있음)
        if (imageUrl == null || imageUrl.isBlank()) return;

        // URL에서 S3 key 추출
        // 형식: https://{bucket}.s3.{region}.amazonaws.com/{key}
        String key = imageUrl.substring(imageUrl.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("S3 이미지 삭제 완료: key={}", key);
        } catch (Exception e) {
            log.error("S3 이미지 삭제 실패: key={}, error={}", key, e.getMessage());
        }
    }
}
