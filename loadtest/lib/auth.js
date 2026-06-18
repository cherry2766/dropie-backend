// 공통 헬퍼 — JWT 토큰 생성 + 설정값
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
export const SECRET      = __ENV.JWT_SECRET  || 'dropie-loadtest-secret-key-32bytes-minimum-please';
export const USER_COUNT  = Number(__ENV.USER_COUNT  || 2000);
export const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '1,2,3,4,5').split(',').map(Number);

// base64url 인코딩 (패딩 없음) — JWT 규격
function b64url(str) {
    return encoding.b64encode(str, 'rawurl');
}

// HS256 JWT 1개 생성 (claim: email, role) — 백엔드 필터는 email만 읽어 DB 유저 조회
export function makeToken(email) {
    const header  = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const now     = Math.floor(Date.now() / 1000);
    const payload = b64url(JSON.stringify({ email: email, role: 'USER', iat: now, exp: now + 86400 }));
    const signingInput = `${header}.${payload}`;
    const signature = crypto.hmac('sha256', SECRET, signingInput, 'base64rawurl');
    return `${signingInput}.${signature}`;
}