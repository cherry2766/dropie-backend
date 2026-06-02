package com.dropie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling	// 스케줄러 기능 켜는 스위치
@EnableAsync  // 추가: @Async 어노테이션이 동작하려면 필요
@SpringBootApplication
public class DropieApplication {

	public static void main(String[] args) { SpringApplication.run(DropieApplication.class, args);}

}
