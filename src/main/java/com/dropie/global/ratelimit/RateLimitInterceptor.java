package com.dropie.global.ratelimit;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

// HandlerInterceptor : 컨트롤러 실행 전/후에 공통 로직을 끼워 넣는 Spring의 인터셉터
// → 필터(Filter)와 비슷하지만 Spring Context를 사용할 수 있어 Bean 주입이 가능
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    // 1분 동안 같은 IP에서 최대 10번 요청 허용
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 요청자의 IP 주소를 키로 사용
        // X-Forwarded-For : 프록시/로드밸런서 뒤에 있을 때 실제 클라이언트 IP가 담기는 헤더
        // → 없으면 직접 연결된 IP(getRemoteAddr())를 사용
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }

        // Redis 키 형식: rate_limit:{IP주소}
        // → 예: rate_limit:192.168.1.1
        String key = "rate_limit:" + ip;

        // Redis에서 현재 요청 횟수 조회
        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr == null ? 0L : Long.parseLong(countStr);

        if (count >= MAX_REQUESTS) {
            // 제한 초과 → 429 Too Many Requests 반환
            // GlobalExceptionHandler가 BusinessException을 잡아 통일된 JSON 응답으로 처리
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        if (count == 0) {
            // 첫 요청이면 카운트를 1로 초기화하고 TTL 설정
            // → TTL이 지나면 Redis에서 자동 삭제되어 횟수가 리셋됨
            redisTemplate.opsForValue().set(key, "1", WINDOW);
        } else {
            // 기존 키의 카운트만 증가 (TTL은 유지)
            redisTemplate.opsForValue().increment(key);
        }

        return true; // true 반환 시 다음 단계(컨트롤러)로 진행
    }
}
