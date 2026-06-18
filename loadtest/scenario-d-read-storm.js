// 시나리오 D — 이벤트 상세/재고 조회 폭주 (read 부하)
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/auth.js';

// 천장 탐색용 ramping — 0명부터 계단식으로 올려 어디서 p95가 깨지는지 본다
const MAX_VUS  = Number(__ENV.MAX_VUS || 2000);  // 최대 VU (PC 버벅이면 낮춰서 실행)
const EVENT_ID = __ENV.EVENT_ID || '1';

// ABORT=false 로 주면 p95가 300ms를 넘어도 중단하지 않고 2000 VU까지 풀 램프(3분) 끝까지 측정.
// → 천장 '곡선' 전체와 풍부한 그래프 캡처용. 기본(true)은 천장 '지점' 탐색용(넘는 순간 자동 중단).
const ABORT = (__ENV.ABORT || 'true') !== 'false';
const p95 = ABORT
    ? [{ threshold: 'p(95)<300', abortOnFail: true, delayAbortEval: '10s' }]
    : ['p(95)<300'];   // 기록만 하고 중단 안 함

export const options = {
    scenarios: {
        read_storm: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 300 },              // 워밍업 (0→300)
                { duration: '1m',  target: Math.floor(MAX_VUS / 2) },  // 중간 (→1000)
                { duration: '1m',  target: MAX_VUS },          // 최대치 (→2000)
                { duration: '30s', target: 0 },                // 정리 (→0)
            ],
        },
    },
    thresholds: {
        'http_req_duration{name:detail}': p95,
        'http_req_duration{name:list}':   p95,
        'http_req_failed': ['rate<0.01'],
    },
};

export default function () {
    const detail = http.get(`${BASE_URL}/events/${EVENT_ID}`, { tags: { name: 'detail' } });
    check(detail, { 'detail 200': (r) => r.status === 200 });

    const list = http.get(`${BASE_URL}/events?status=OPEN`, { tags: { name: 'list' } });
    check(list, { 'list 200': (r) => r.status === 200 });
}