package com.dropie.global.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

// JWT 토큰을 만들고, 읽고, 유효한지 검증
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    // application.yml의 jwt.secret 값을 주입받아서
    // HMAC-SHA256 서명용 키 객체로 변환
    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                Jwts.SIG.HS256.key().build().getAlgorithm()
        );
    }

    // 토큰에서 이메일 추출
    // parseSignedClaims : 서명 검증 + 파싱을 동시에 함
    // getPayload().get("email") : payload 부분에서 email 클레임 꺼냄
    public String getEmail(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("email", String.class);
    }

    // 토큰에서 role 추출
    public String getRole(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    // 토큰 생성 (email, role, 만료시간 받아서 JWT 문자열 반환)
    public String createToken(String email, String role, Long expiredMs) {
        return Jwts.builder()
                .claim("email", email)  // payload에 email 저장
                .claim("role", role)     // payload에 role 저장
                .issuedAt(new Date(System.currentTimeMillis()))     // 발급 시간
                .expiration(new Date(System.currentTimeMillis() + expiredMs))   // 만료 시간
                .signWith(secretKey)    // 비밀키로 서명
                .compact();             // 최종 JWT 문자열 생성
    }

    // Refresh Token 생성
    // Access Token(JWT)과 달리 Refresh Token은 DB에서 비교 검증하기 때문에
    // 굳이 JWT 형식으로 만들 필요 없음 → UUID(랜덤 문자열)로 만들면 충분
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }
}
