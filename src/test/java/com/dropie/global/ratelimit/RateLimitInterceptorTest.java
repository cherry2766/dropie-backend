package com.dropie.global.ratelimit;

import com.dropie.global.exception.BusinessException;
import com.dropie.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @InjectMocks
    private RateLimitInterceptor interceptor;

    @Mock
    private StringRedisTemplate redisTemplate;

    // opsForValue() 체인 호출을 위해 ValueOperations Mock 별도 선언
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        // opsForValue()만 공통 세팅 — IP 관련 stubbing은 각 테스트에서 직접 세팅
        // → X-Forwarded-For 유무에 따라 getRemoteAddr() 호출 여부가 달라지기 때문
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("첫 요청은 카운트 1로 초기화 후 통과")
    void 첫_요청_통과() throws Exception {
        // given
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("1.2.3.4");
        // Redis에 키 없음 → 첫 요청 상황
        given(valueOperations.get("rate_limit:1.2.3.4")).willReturn(null);

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        // 첫 요청은 increment가 아닌 set()으로 초기화해야 함 (TTL을 함께 설정해야 하기 때문)
        then(valueOperations).should().set(
                eq("rate_limit:1.2.3.4"),
                eq("1"),
                eq(Duration.ofMinutes(1))
        );
    }

    @Test
    @DisplayName("요청 횟수가 제한 이하면 increment 후 통과")
    void 제한_이하_요청_통과() throws Exception {
        // given
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("1.2.3.4");
        // 이미 5번 요청한 상태
        given(valueOperations.get("rate_limit:1.2.3.4")).willReturn("5");

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        // count > 0 이므로 set이 아닌 increment 사용 (set 쓰면 TTL이 리셋돼버림)
        then(valueOperations).should().increment("rate_limit:1.2.3.4");
    }

    @Test
    @DisplayName("10번째 요청은 통과 (경계값: count=9 < MAX=10)")
    void 열번째_요청_통과() throws Exception {
        // given
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("1.2.3.4");
        // count=9 → 10번째 요청 (MAX_REQUESTS=10 이므로 9 < 10 → 통과)
        given(valueOperations.get("rate_limit:1.2.3.4")).willReturn("9");

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        // 경계값 테스트: 10번째(count=9)는 통과, 11번째(count=10)는 차단
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("11번째 요청은 TOO_MANY_REQUESTS 예외 발생 (경계값: count=10 >= MAX=10)")
    void 요청_초과_예외() {
        // given
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("1.2.3.4");
        // count=10 → MAX_REQUESTS(10) 이상 → 차단
        given(valueOperations.get("rate_limit:1.2.3.4")).willReturn("10");

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더가 있으면 해당 IP를 키로 사용")
    void XForwardedFor_헤더_IP_사용() throws Exception {
        // given
        // 로드밸런서/프록시 뒤에서 실제 클라이언트 IP가 X-Forwarded-For에 담김
        given(request.getHeader("X-Forwarded-For")).willReturn("9.8.7.6");
        given(valueOperations.get("rate_limit:9.8.7.6")).willReturn(null);

        // when
        interceptor.preHandle(request, response, new Object());

        // then
        // getRemoteAddr()가 아닌 X-Forwarded-For 값으로 키가 생성됐는지 검증
        then(valueOperations).should().set(
                eq("rate_limit:9.8.7.6"),
                eq("1"),
                eq(Duration.ofMinutes(1))
        );
    }
}