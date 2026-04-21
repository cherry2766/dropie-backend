package com.dropie.global.s3;

import com.dropie.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        // @Value 필드는 스프링 컨텍스트가 없으면 주입이 안 돼서 ReflectionTestUtils로 직접 주입
        ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "ap-northeast-2");
    }

    @Test
    @DisplayName("Presigned URL 생성 성공 — presignedUrl과 imageUrl 정상 반환")
    void Presigned_URL_생성_성공() throws Exception {
        // given
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(presignedRequest.url()).willReturn(
                // 실제 Presigned URL에는 ?X-Amz-Algorithm=... 같은 쿼리 파라미터가 붙지만
                // 여기서는 Mock이라 의미 없음 — 단순한 URL로 설정하고 null 여부만 검증
                new URL("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uuid-test.jpg")
        );
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presignedRequest);

        // when
        PresignedUrlResponse response = s3Service.generatePresignedUrl("test.jpg", "image/jpeg");

        // then
        assertThat(response.getPresignedUrl()).isNotNull(); // presignedUrl이 정상적으로 반환됨
        assertThat(response.getImageUrl()).startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/");
        assertThat(response.getImageUrl()).endsWith(".jpg"); // UUID.확장자 형식
    }

    @Test
    @DisplayName("Presigned URL 생성 실패 — S3 오류 시 BusinessException 발생")
    void Presigned_URL_생성_실패() {
        // given
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willThrow(new RuntimeException("S3 연결 오류"));

        // when & then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl("test.jpg", "image/jpeg"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("이미지 삭제 성공 — S3Client.deleteObject 호출")
    void 이미지_삭제_성공() {
        // given
        String imageUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uuid-test.jpg";

        // when
        s3Service.deleteImage(imageUrl);

        // then — S3Client에 삭제 요청이 정확히 1번 호출됨
        then(s3Client).should().deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("이미지 URL이 null이면 S3 삭제 호출하지 않음")
    void 이미지_삭제_null_URL() {
        // when
        s3Service.deleteImage(null);

        // then
        then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("이미지 URL이 빈 문자열이면 S3 삭제 호출하지 않음")
    void 이미지_삭제_빈_URL() {
        // when
        s3Service.deleteImage("");

        // then
        then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("S3 삭제 실패 시 예외를 던지지 않고 로그만 남김 — DB 삭제는 정상 진행되어야 함")
    void 이미지_삭제_S3_실패_예외_미발생() {
        // given
        String imageUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uuid-test.jpg";
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(new RuntimeException("S3 오류"));

        // when & then — 예외가 바깥으로 전파되지 않아야 함
        assertThatCode(() -> s3Service.deleteImage(imageUrl)).doesNotThrowAnyException();
    }
}
