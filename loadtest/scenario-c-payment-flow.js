// 시나리오 C — 결제 종단 흐름 + 멱등성
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { makeToken, BASE_URL, USER_COUNT, PRODUCT_IDS } from './lib/auth.js';

const VUS  = Number(__ENV.VUS || 200);
const ITER = Number(__ENV.ITERATIONS || 300);   // 총 주문 수 (재고 500 내라 품절 안 남)

const completed = new Counter('payment_completed');
const idemOk    = new Counter('payment_idempotent_ok');
const failed    = new Counter('payment_failed');
const orderDur  = new Trend('order_duration', true);
const payDur    = new Trend('payment_duration', true);

export const options = {
    scenarios: {
        payment_flow: { executor: 'shared-iterations', vus: VUS, iterations: ITER, maxDuration: '3m' },
    },
    thresholds: { 'payment_failed': ['count<1'], 'checks': ['rate>0.99'] },
};

export default function () {
    const uid = ((__VU - 1) % USER_COUNT) + 1;
    const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${makeToken(`user${uid}@loadtest.local`)}` };

    // 1) 주문 생성 (상품 1개 — 재고 보존)
    const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
    const orderRes = http.post(`${BASE_URL}/orders`, JSON.stringify({
        receiverName: 'loadtest', phone: '010-0000-0000', zipcode: '00000', address1: 'addr',
        items: [{ productId: productId, quantity: 1 }],
    }), { headers });
    orderDur.add(orderRes.timings.duration);
    if (orderRes.status !== 201) { failed.add(1); return; }
    const order = orderRes.json();

    // 2) 결제 confirm (mock)
    const payBody = JSON.stringify({ paymentKey: `mock_${order.orderNumber}`, amount: order.totalPrice });
    const pay1 = http.post(`${BASE_URL}/orders/${order.orderId}/payment/confirm`, payBody, { headers });
    payDur.add(pay1.timings.duration);
    if (check(pay1, { '결제 200': (r) => r.status === 200 })) completed.add(1);
    else { failed.add(1); return; }

    // 3) 멱등성 — 같은 confirm 다시 (200 + 같은 orderId, 결제는 1건만)
    const pay2 = http.post(`${BASE_URL}/orders/${order.orderId}/payment/confirm`, payBody, { headers });
    if (check(pay2, {
        '재요청도 200': (r) => r.status === 200,
        '같은 orderId': (r) => r.json('orderId') === order.orderId,
    })) idemOk.add(1);
}