-- Dropie 부하측정 시드 데이터
-- ⚠️ 반드시 "앱이 뜬 뒤"에 실행 (테이블은 Hibernate가 부팅 때 생성하므로)
SET SESSION cte_max_recursion_depth = 5000;

-- 1) 온보딩 태그 10개 (tags는 BaseEntity 아님 → 타임스탬프 컬럼 없음)
INSERT INTO tags (name, onboarding_exposed) VALUES
                                                ('sweet',1),('crispy',1),('creamy',1),('fruit',1),('chocolate',1),
                                                ('greentea',1),('bread',1),('donut',1),('nutty',1),('seasonal',1);

-- 2) 관리자 (DataInitializer가 하던 것 대체)
INSERT INTO users (email, password, nickname, role, email_verified, onboarding_skipped, created_at, updated_at)
VALUES ('admin@loadtest.local','no-login','loadadmin','ADMIN',1,1,NOW(),NOW());

-- 3) 이벤트 1개 (OPEN, start_at을 과거로 둬서 판매 이미 시작된 상태)
INSERT INTO events (brand_name, description, status, start_at, end_at, created_at, updated_at)
VALUES ('LoadTest Drop','load test event','OPEN', NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 30 DAY, NOW(), NOW());

-- 4) 상품 5개 (각 재고 100) — version=0 필수 (@Version 낙관적 락 컬럼)
INSERT INTO products (event_id, name, price, stock, version, created_at, updated_at)
SELECT e.id, p.name, p.price, 100, 0, NOW(), NOW()
FROM events e
         JOIN (
    SELECT 'LoadTest Product 1' AS name, 3000 AS price
    UNION ALL SELECT 'LoadTest Product 2',3500
    UNION ALL SELECT 'LoadTest Product 3',4000
    UNION ALL SELECT 'LoadTest Product 4',3200
    UNION ALL SELECT 'LoadTest Product 5',4500
) p
WHERE e.brand_name='LoadTest Drop';

-- 5) 테스트 유저 2000명 (k6가 이 이메일로 JWT를 직접 만들어 인증 → 비번 미사용)
INSERT INTO users (email, password, nickname, role, email_verified, onboarding_skipped, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 2000
)
SELECT CONCAT('user',n,'@loadtest.local'),'no-login',CONCAT('loaduser',n),'USER',1,0,NOW(),NOW()
FROM seq;