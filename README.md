<img width="408" height="172" alt="dropie_logo" src="https://github.com/user-attachments/assets/8bbe3c8d-aa17-4b80-99cc-055a2bdc2c12" />

# 🍩 Dropie — 한정판 디저트 드롭 플랫폼

<blockquote>
특정 시간 오픈 · 한정 수량 · 선착순
<br>
멀리 가지 않아도, 웨이팅하지 않아도 — 온라인으로 여는 디저트 팝업
</blockquote>

📍 [Live Demo](https://dropie.shop) · 📖 [API Docs (Swagger)](https://api.dropie.shop/swagger-ui.html) · 🎨 Frontend [dropie-frontend](https://github.com/cherry2766/dropie-frontend)


<br><br>

## 🖥️ 프로젝트 소개

<img width="1536" height="1024" alt="드로피 주문흐름" src="https://github.com/user-attachments/assets/27e3580c-4754-4d15-91c1-90fa472a172d" />

<h3>
🍰 인기 디저트 팝업을 온라인으로 옮긴 한정판 디저트 드롭 플랫폼
</h3>

<br>

<blockquote>
  
📅 **개발 기간** : 2026.03.31 ~ 2026.05.23 (약 8주)

👤 **개발 인원** : 1명 (기획 → 설계 → 백엔드 → 프론트 → 배포 → 부하테스트 전 과정)

🚀 **배포 환경** : AWS EC2(t3.micro) + RDS(MySQL 8.0) + S3 + CloudFront / Docker Compose / Nginx + Let's Encrypt HTTPS

🔑 **핵심 키워드** : 동시성 제어 · Redis 다목적 활용 · AI 추천 · 분리형 클라우드 배포 · 부하테스트 정합성 증명

</blockquote>

<br><br>

## 💡기획 의도

<blockquote>
  
오프라인 디저트 팝업은 멀어서 못 가거나, 가더라도 긴 웨이팅 때문에 구매를 못 하는 경우가 많다.
<br>
팝업 경험을 온라인으로 옮겨, 정해진 시간에 열리는 한정 수량 디저트를 누구나 선착순으로 구매할 수 있도록 했다.

</blockquote>

🛒 **이용자** — 멀리 가거나 웨이팅하지 않아도 한정 디저트 구매
<br>
🏪 **입점 브랜드** — 드롭 등록만으로 자연스러운 온라인 홍보 효과


<br><br>

### ✔️ 핵심 과제

|  | 과제 | 이유 |
|---|---|---|
| 1 | **동시 주문 재고 정합성** | 드롭 오픈 순간 수백 요청이 같은 재고를 동시에 읽고 차감 → 음수 재고 · 초과 판매 위험 |
| 2 | **결제 이탈 PENDING 회수** | 사용자가 결제창을 닫으면 주문이 PENDING으로 남아 재고가 영구 점유됨 |
| 3 | **콜드스타트 추천** | 구매 이력이 없는 신규/기존 회원에게도 빈 추천이 아닌 의미 있는 추천을 줘야 함 |
| 4 | **외부 API 장애 격리** | 추천/Claude/태그 호출이 결제 트랜잭션에 묶이면 외부 장애가 결제를 롤백시킴 |

<br>

### ✔️ 핵심 설계

|  | 과제 | 설계 |
|---|---|---|
| 1 | **재고 정합성** | **Redisson 분산 락 + @Version 낙관 락** (현재 운영 = 분산락 모드)<br>두 전략은 `app.lock.type`으로 전환 — 부하테스트로 비교해 분산락 채택<br>다중 상품 주문은 MultiLock을 상품 ID 순 정렬 획득해 데드락 차단 |
| 2 | **PENDING 회수** | **Redis Keyspace Notification**<br>`pending_order:{id}` TTL 15분 <br> 만료 이벤트로 PENDING→CANCELED + 재고 복구 5분 배치 안전망 병행 |
| 3 | **콜드스타트** | **3중 폴백**<br>가입 시드(+0.5) → 구매(+1.0) ZSET<br>비면 DB lazy 동기화 → 부족하면 인기 TOP10 |
| 4 | **외부 API 격리** | **AFTER_COMMIT + @Async**<br>추천/태그 누적·Claude 호출을 결제 트랜잭션 이후 비동기로 분리<br>외부 장애가 결제를 롤백시키지 않음 |

<details>
<summary><b>Redis 6가지 용도</b></summary>

| 용도 | 키 | 비고 |
|---|---|---|
| 분산 락 | Redisson `stock:lock:{id}` | 동시 주문 직렬화 |
| 사용자 취향 ZSET | `user:taste:{userId}` | 시드(+0.5) + 구매(+1.0), 90일간 구매 없으면 자동 만료 |
| 인기 이벤트 ZSET | `popularity:event:{yyyyMMdd}` | 최근 7일 합산(`:top:7d`) TOP10, 콜드스타트 폴백 |
| 추천 캐시 | `recommendation:user:{userId}` | TTL 60분, 빈 결과 미캐시 |
| Pub/Sub Keyspace | `pending_order:{id}` | TTL 만료 → PENDING 자동 취소 |
| 주문번호 시퀀스 | `order:seq:{yyyyMMdd}` | INCR, 다중 인스턴스 안전 |

추가로 refresh / 이메일 인증 / 비밀번호 재설정 토큰 저장소로도 활용, AOF로 영속화

</details>

<br><br>

## 🛠️ 기술 스택

### Backend
![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-2E7D32?style=for-the-badge&logo=springsecurity&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_Redisson-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket_+_STOMP-35495E?style=for-the-badge&logo=spring&logoColor=white)

### Frontend
![React](https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![Zustand](https://img.shields.io/badge/Zustand-443E38?style=for-the-badge)
![TanStack Query](https://img.shields.io/badge/TanStack_Query-FF4154?style=for-the-badge&logo=reactquery&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)
![shadcn/ui](https://img.shields.io/badge/shadcn%2Fui-000000?style=for-the-badge&logo=shadcnui&logoColor=white)

### Infrastructure
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![AWS EC2](https://img.shields.io/badge/AWS_EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white)
![AWS RDS](https://img.shields.io/badge/AWS_RDS-527FFF?style=for-the-badge&logo=amazonrds&logoColor=white)
![S3](https://img.shields.io/badge/AWS_S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white)
![CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?style=for-the-badge&logo=amazoncloudfront&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

### External 
![Toss Payments](https://img.shields.io/badge/Toss_Payments-0064FF?style=for-the-badge&logo=tosspayments&logoColor=white)
![Claude API](https://img.shields.io/badge/Claude_API-D97757?style=for-the-badge&logo=anthropic&logoColor=white)

> 동시성 제어(Redisson 분산 락) · 실시간(WebSocket + STOMP) · AI 추천(Claude API) · 비동기(@Async)

<br><br>

## 🏗️ 시스템 아키텍처
<img width="1000" alt="드로피 아키텍처" src="https://github.com/user-attachments/assets/8e5b5ad6-5747-4028-bc86-e895c8acb203" />

> 정적 프론트 / 동적 백엔드 / DB를 물리적으로 분리한 클라우드 구성

<br><br>

## 🔄 CI/CD
<img width="1000" alt="드로피 cicd 파이프라인" src="https://github.com/user-attachments/assets/985262ba-cc5b-4d08-9e8d-2812a4eb2619" />

> `git push main` → GitHub Actions 자동 테스트 → 배포까지 자동화

<br><br>

## 🧩 ERD
<img width="1000" alt="드로피ERD" src="https://github.com/user-attachments/assets/ab9a8165-648b-460c-af2d-841bdd84cea7" />

> 모든 엔티티는 `BaseEntity` 상속 → `created_at` / `updated_at` 자동 관리

<br><br>

## 🎬 주요 기능 데모

| 진행중 드롭 목록 | 이벤트 상세 |
|---|---|
| <img width="420" height="740" alt="드로피 메인3" src="https://github.com/user-attachments/assets/842b7283-9988-4086-ab3e-9379bc415a1a" /> | <img width="420" height="740" alt="드로피 이벤트 상세" src="https://github.com/user-attachments/assets/5839dc08-f626-49a5-aacf-9ce3795a8f52" /> |

| AI 개인화 추천 | 오픈 예정 라인업 & 인기 TOP10 |
|---|---|
| <img width="420" height="740" alt="드로피 AI개인화 추천" src="https://github.com/user-attachments/assets/64bb78d9-1654-49d1-93c9-15dc8edb0555" /> | <img width="420" height="740" alt="드로피 오픈예정 탑텐" src="https://github.com/user-attachments/assets/2ef8a9a8-e49f-4e15-8af6-43f0c505fde9" /> |

| 주문 확인 | 결제 완료 |
|---|---|
| <img width="420" height="740" alt="드로피 주문_결제" src="https://github.com/user-attachments/assets/724232ac-0d24-47ae-94c6-ee9a886e1e85" /> | <img width="420" height="740" alt="드로피 결제완료" src="https://github.com/user-attachments/assets/6123891b-75bc-450e-afb8-b0a5e2b822bf" /> |

| 마이페이지 — 주문내역 | 마이페이지 — 배송지 관리 |
|---|---|
| <img width="420" height="740" alt="드로피 주문내역" src="https://github.com/user-attachments/assets/a0ada0d5-8ce3-48b2-a497-62824c78d6a1" /> | <img width="420" height="740" alt="드로피 배송지관리" src="https://github.com/user-attachments/assets/1f0be806-4c64-4bea-b15c-883c3275b408" /> |

| 관리자 — 이벤트 등록 | 관리자 — 상품 등록 |
|---|---|
| <img width="420" height="460" alt="드로피 관리자 이벤트" src="https://github.com/user-attachments/assets/b10f3c95-6a67-48d2-986e-d77e7203b839" /> | <img width="420" height="460" alt="드로피 관리자 상품" src="https://github.com/user-attachments/assets/5a65358c-9fa0-4a0d-9715-967f558326b9" /> |

<br><br>

## ⚙️ 핵심 기술 구현

- **① 분산 락 MultiLock** — 상품 ID 정렬 순으로 락 획득(Redisson MultiLock) → 다중 상품 주문 데드락 원천 차단 (실측: 데드락 0 vs 82)

- **② Keyspace Notification** — `pending_order:{id}` TTL 15분 만료 이벤트로 PENDING→CANCELED + 재고 복구 (5분 배치 병행)

- **③ ZSET 추천** — 가입 시드(+0.5) + 구매(+1.0)를 ZSET 하나로 통합, 비면 DB lazy 복구 → 콜드스타트 해결

- **④ INCR 주문번호** — Redis `INCR order:seq:{yyyyMMdd}`로 다중 인스턴스 중복 없는 일별 번호 (TTL 다음날 자정)

- **⑤ 결제 실패 보상 트랜잭션** — 결제 실패 시 `AFTER_ROLLBACK` + `REQUIRES_NEW`로 주문 취소/재고 복원 (멱등)

<br><br>

## 📈 부하 테스트

> ⚠️ 단일 머신 측정이라 절대 수치는 참고용 — **정합성 + 상대 비교**

<br>

### 정합성 

| 시나리오 | 결과 |
|---|---|
| 다중상품 주문 폭주 (1000 VU) | 음수재고 0 · 판매량 정합(339=339) · 5xx 0 |
| 결제 멱등성 (confirm 600회) | payments 300 · 중복결제 0 |
| 워스트: 재고 1개·1000명 | 정확히 1건만 성공 · 음수재고 0 |

<br>

### 분산락 vs 낙관락

| 지표 | 분산락 | 낙관락 |
|---|---|---|
| 음수재고 | 0 | 0 |
| 201 성공 | 155 | 48 |
| MySQL 데드락 | **0** | **82** |

→ 낙관락은 다중상품에서 데드락 82건, 분산락은 정렬 락으로 0 → **분산락 채택** (JUnit 동시성 테스트도 동일 결론)

<br><br>

### 처리량 — 조회 폭주

<img width="1903" height="852" alt="시나리오D_1차" src="https://github.com/user-attachments/assets/85d44f8d-4594-40a7-a2cc-83322a331bed" />


> 조회 폭주: 2000 VU·50만 요청 에러 0% (p95 ~1.25s)

<br>

### 정합성 — 워스트 케이스

<img width="576" height="413" alt="시나리오E_DB_1차" src="https://github.com/user-attachments/assets/c7e4bf11-369e-41c8-8c6c-296abe8e92f1" />

<br><br>

## 🧯 트러블슈팅

<details>
<summary><b>① 재고 동시 차감으로 음수 재고 발생</b></summary>

- **문제** — 드롭 열리는 순간 수백 명이 같은 상품을 동시에 주문하니 재고가 -3, -7까지 내려감
- **원인** — 다 같은 재고값을 읽고 각자 차감하면서 갱신 손실(race condition)
- **해결** — `app.lock.type`으로 락 전략을 토글하게 설계
    - `@Version` 낙관 락: 충돌 시 `@Retryable` 3회 재시도, 소진되면 `ORDER_CONFLICT 409`
    - Redisson 분산 락(`stock:lock:{id}`): 다중 상품일 땐 **productId 오름차순으로 MultiLock 획득**해 데드락 차단 (`tryLock` 5초·leaseTime 3초, 실패 시 `LOCK_ACQUISITION_FAILED 503`)
    - 부하테스트로 비교해 충돌 많은 구간에 강한 분산 락으로 결정 (데드락 0 vs 낙관락 82)

</details>

<details>
<summary><b>② 결제 미완료(PENDING) 주문이 재고를 영구 점유</b></summary>

- **문제** — 사용자가 결제창을 닫으면 주문이 PENDING으로 남아 재고가 안 풀림
- **원인** — 결제는 사용자 행동에 달린 비동기 흐름이라 백엔드가 "끝났는지"를 알 방법이 없음
- **해결** — `pending_order:{id}`에 TTL 15분을 걸고 Keyspace Notification(`expired`)을 구독 → 만료되면 자동으로 PENDING 취소 + 재고 복구 (놓칠 때 대비해 5분 배치도 같이 돌림)

</details>

<details>
<summary><b>③ 이벤트 시작/종료 시각이 화면 상태에 반영되지 않음</b></summary>

- **문제** — `startAt`이 지났는데 아직 `UPCOMING`, `endAt` 지나도 `OPEN`으로 떠 있음
- **원인** — DB `status`가 관리자가 바꿀 때만 갱신돼서 시간 흐름이랑 안 맞음
- **해결** — 두 군데서 같이 보정
    - 1분 주기 `EventStatusScheduler`로 시간 기반 자동 전환(UPCOMING→OPEN, OPEN→CLOSED, 재오픈)
    - 응답 시점엔 `EventStatusCalculator`가 `(DB status, startAt, endAt, 전상품 품절)`로 **Derived Status** 계산 (우선순위 FINISHED > CLOSED > SOLD_OUT > 시간) → 스케줄러 최대 1분 지연을 응답에서 메움

</details>

<details>
<summary><b>④ 기존 회원에게 AI 추천이 빈 배열로 떨어짐</b></summary>

- **문제** — 시드 코드 도입 전에 가입한 회원은 추천이 비고, 60분 캐시에 빈 배열이 박혀 데이터가 들어와도 한 시간 동안 안 보임
- **원인**
    - 취향 시드가 회원가입 때만 돌아서 기존 회원엔 한 번도 안 일어남
    - 빈 결과도 그냥 캐시해서, 그 사이 주문·시드가 들어와도 60분간 빈 상태로 노출
- **해결**
    - 추천 조회 때 `syncFromDbIfEmpty()`로 ZSET 비면 DB의 가입 태그(+0.5)·기존 PAID 주문(+1.0)을 한 번 시드 (멱등, Redis 휘발 복구도 겸함)
    - 빈 결과는 캐시 안 해서 데이터 차면 다음 호출에 바로 반영

</details>

<details>
<summary><b>⑤ 탈퇴 후 동일 이메일로 재가입이 영구 불가</b></summary>

- **문제** — 소프트 딜리트로 `deletedAt`만 찍고 `email`은 남겨서, unique 제약 때문에 같은 이메일로 재가입하면 `DUPLICATE_EMAIL`
- **원인** — 개인정보 보존 의무랑 `email` unique 제약이 충돌
- **해결** — 즉시 처리와 유예 처리를 나눔
    - 탈퇴 즉시: 닉네임 `탈퇴회원_{id}` 마스킹 + Refresh Token 삭제
    - 30일간: `RECENTLY_WITHDRAWN_EMAIL 409`로 재가입 막음 (번복 방지)
    - 30일 후: 매일 자정 `UserMaskingScheduler`가 `email`을 `withdrawn_{id}@masked.local`로 바꿔 재가입 가능 (`idx_users_deleted_at`로 배치 조회 가속)

</details>

<details>
<summary><b>⑥ CI가 SSH로 막힘 → 포트 안 열고 SSM 배포</b></summary>

- **문제** — GitHub Actions가 SSH로 EC2에 배포하려다 `dial tcp ...:22: i/o timeout`
- **원인** — 보안그룹이 SSH(22)를 내 IP만 허용해서 러너(매번 바뀌는 IP)가 막힘
- **해결** — 22를 열지 않고 **OIDC + AWS SSM**으로 전환 → OIDC로 장기 키 없이 단기 자격증명만 받고, SSM `send-command`(AWS-RunShellScript)로 EC2의 `deploy.sh`를 원격 실행

</details>

<br><br>

## 📁 폴더 구조

~~~text
com.dropie
├── domain
│   ├── order                 # 주문 (예시) — 모든 도메인이 아래 5계층 동일 구조
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto               # request / response
│   ├── user
│   ├── event
│   ├── product
│   ├── payment
│   ├── address
│   ├── tag
│   ├── preference
│   └── recommendation
└── global
    ├── common                # BaseEntity, PageResponse
    ├── config
    ├── exception             # BusinessException, ErrorCode, GlobalExceptionHandler
    ├── security              # JWT, CustomUserDetails
    ├── aop
    ├── email
    ├── ratelimit
    └── s3
~~~

<br><br>

## ️▶️ 실행 방법

~~~bash
# 1. 환경변수 준비
cp .env.example .env        # 값 채우기 (DB, JWT, AWS, Toss, Claude 키 등)

# 2. 로컬 실행 (Docker Compose — 앱 + Redis + MySQL)
docker compose -f docker-compose.local.yml up -d --build

# 또는 Gradle 직접 실행
./gradlew bootRun

# 3. 테스트
./gradlew test
~~~
