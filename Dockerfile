# 멀티스테이지 빌드: 빌드 단계와 실행 단계 분리 → 최종 이미지 크기 축소

# === 1단계: 빌드 ===
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew && ./gradlew clean bootJar -x test

# === 2단계: 실행 ===
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
# 시간대 한국으로 (createdAt, 만료시간 등 시간 오차 방지)
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]