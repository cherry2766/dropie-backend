package com.dropie.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect // AOP 역할
@Component
public class LoggingAspect {

    // @Around: 메서드 실행 전후 모두 개입
    // "com.dropie.domain..service..*(..)" : domain 패키지 안의 모든 service 패키지, 모든 메서드에 적용
    @Around("execution(* com.dropie.domain..service..*(..))")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {

        // 실행되는 클래스명, 메서드명 가져오기
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long start = System.currentTimeMillis();

        // 메서드 실행 전 로그
        log.info("[{}] {}.{}() 시작", "SERVICE", className, methodName);

        // 실제 서비스 메서드 실행
        Object result = joinPoint.proceed();

        long end = System.currentTimeMillis();

        // 메서드 실행 후 로그(소요시간 포함)
        log.info("[{}] {}.{}() 종료 - {}ms", "SERVICE", className, methodName, end - start);

        return result;
    }
}
