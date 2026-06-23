// 시나리오 A — 다중 상품 주문 폭주 (MultiLock + 데드락 방지 경로)
//
// 이 파일 하나로 A/B/E 세 시나리오를 모두 실행한다 (env/락 설정만 바꿔 재사용)
//   A: k6 run scenario-a-order-burst.js
//   B: 백엔드를 APP_LOCK_TYPE=optimistic 으로 재기동 후 동일 실행 → redis 결과와 처리량/p95 비교
//   E: PRODUCT_IDS=1 MIN_ITEMS=1 MAX_ITEMS=1 로 실행 (재고 1개에 1000명 워스트 경합)
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { makeToken, BASE_URL, USER_COUNT, PRODUCT_IDS } from './lib/auth.js';

const VUS       = Number(__ENV.VUS || 1000);
const MIN_ITEMS = Number(__ENV.MIN_ITEMS || 2);
const MAX_ITEMS = Number(__ENV.MAX_ITEMS || 3);

const success     = new Counter('order_success');        // 201 주문 성공
const rejected    = new Counter('order_rejected_409');   // 409 품절/충돌 (정상 거절)
const lockFail    = new Counter('order_lock_fail_503');  // 503 분산락 획득 실패 (정상 백프레셔)
const serverError = new Counter('order_server_error');   // 예상 외(5xx 등) — 0이어야 함
const okDuration  = new Trend('order_success_duration', true);

export const options = {
    scenarios: {
        drop_burst: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '2m' },
    },
    thresholds: {
        'order_server_error': ['count<1'],          // 서버 에러 0건
        'order_success_duration': ['p(95)<2000'],   // 성공 주문 p95 (1차 측정 보고 조정)
        'checks': ['rate>0.99'],
    },
};

// 서로 다른 n개 뽑기 (같은 상품 2번이면 DUPLICATE_ORDER_ITEM 409 → 반드시 distinct)
function pickDistinct(arr, n) {
    const pool = arr.slice();
    const out = [];
    for (let i = 0; i < n && pool.length; i++) {
        out.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
    }
    return out;
}

export default function () {
    const uid = ((__VU - 1) % USER_COUNT) + 1;
    const token = makeToken(`user${uid}@loadtest.local`);

    const count = MIN_ITEMS + Math.floor(Math.random() * (MAX_ITEMS - MIN_ITEMS + 1));
    const items = pickDistinct(PRODUCT_IDS, Math.min(count, PRODUCT_IDS.length))
        .map((id) => ({ productId: id, quantity: 1 }));

    const payload = JSON.stringify({
        receiverName: 'loadtest', phone: '010-0000-0000',
        zipcode: '00000', address1: 'load test addr', items: items,
    });

    const res = http.post(`${BASE_URL}/orders`, payload, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    });

    if (res.status === 201)      { success.add(1); okDuration.add(res.timings.duration); }
    else if (res.status === 409) { rejected.add(1); }   // OUT_OF_STOCK 또는 ORDER_CONFLICT
    else if (res.status === 503) { lockFail.add(1); }   // LOCK_ACQUISITION_FAILED
    // 재고가 전부 팔리면 이벤트가 SOLD_OUT으로 전환됨 → 이후 주문은 400 EVENT_SOLD_OUT (정상 거절)
    else if (res.status === 400 && res.json('code') === 'EVENT_SOLD_OUT') { rejected.add(1); }
    else                         { serverError.add(1); }

    check(res, { '정상 분류(201/409/503/품절)': (r) =>
        r.status === 201 || r.status === 409 || r.status === 503 ||
        (r.status === 400 && r.json('code') === 'EVENT_SOLD_OUT') });
}