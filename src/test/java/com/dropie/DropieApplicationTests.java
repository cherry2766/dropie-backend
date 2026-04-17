package com.dropie;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DropieApplicationTests {

	// 테스트 환경에서 외부 인프라(Redis, Mail) 없이 컨텍스트 로드 가능하게 Mock 처리
	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private JavaMailSender javaMailSender;

	@MockitoBean
	private RedissonClient redissonClient;

	@Test
	void contextLoads() {
	}

}
