package com.dropie.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    // application.yml에 설정한 값을 읽어옴
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    /**
     * S3Presigner: Presigned URL을 만들어주는 클라이언트
     * 일반 S3Client와 달리 서버에서 직접 파일을 올리지 않고,
     * "이 URL로 10분 안에 업로드 가능합니다" 라는 임시 허가증(URL)만 발급하는 역할
     */
    @Bean
    public S3Presigner s3Presigner() {
        // IAM 액세스 키 + 시크릿 키로 AWS 인증 정보 생성
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    /**
     * S3Client: 실제 S3 작업(파일 삭제 등)을 수행하는 클라이언트
     * S3Presigner는 URL 발급 전용이라 삭제 같은 직접 작업은 S3Client가 필요
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
