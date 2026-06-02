package com.dropie.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// @Async 활성화 + 전용 ThreadPool 설정
//
// 전용 풀이 필요한 이유
//   - 기본 풀은 SimpleAsyncTaskExecutor → 호출마다 새 스레드 → 부하 시 폭주
//   - 메일 전송, 스케줄러 등 다른 비동기 작업과 풀을 공유하면
//     하나의 작업이 풀을 점유했을 때 다른 작업이 줄줄이 막힘
//   → 도메인 이벤트 발행용 풀을 따로 두어 격리
@Configuration
@EnableAsync
public class AsyncConfig {

    // 빈 이름으로 @Async("domainEventExecutor")처럼 지정해서 사용
    @Bean(name = "domainEventExecutor")
    public Executor domainEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // CPU 코어 수에 따라 조정
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // 큐 크기. 풀이 꽉 차면 여기에 쌓임
        executor.setQueueCapacity(100);
        // 풀이 꽉 차고 큐도 꽉 찼을 때 정책: 호출 스레드가 직접 실행
        // → 메시지 유실보다 잠깐의 동기 처리가 낫다는 판단
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("domain-event-");
        // 앱 종료 시 진행 중 작업이 끝날 때까지 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
