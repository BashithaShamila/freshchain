// k6 load profile for order placement.
//
//   make load
//
// Placement is the interesting path to load-test because it is the one that
// takes row locks: every order competing for the same SKU serialises on the
// same lots. Reads are included at a realistic ratio so the numbers are not
// flattered by testing writes alone.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const GATEWAY = __ENV.GATEWAY_URL || 'http://gateway:8080';
const WAREHOUSE = __ENV.WAREHOUSE_ID || 'WH-COL-01';


const SKUS = ['CHK-BRST-5LB', 'CHK-THGH-5LB', 'BEF-GRND-10LB', 'MLK-WHL-4GAL', 'LET-ROM-24CT'];

const placementLatency = new Trend('order_placement_duration', true);
const rejected = new Counter('orders_rejected');
const rateLimited = new Counter('requests_rate_limited');

// Profile knobs, so the same script serves a quick smoke run and a longer one:
//   QUICK=1 k6 run order-placement.js
const BASE_VUS = Number(__ENV.BASE_VUS || 20);
const PEAK_VUS = Number(__ENV.PEAK_VUS || 60);
const QUICK = __ENV.QUICK === '1';

const STAGES = QUICK
  ? [
      { duration: '10s', target: BASE_VUS },
      { duration: '40s', target: BASE_VUS },
      { duration: '10s', target: 0 },
    ]
  : [
      { duration: '30s', target: BASE_VUS },  // warm up
      { duration: '2m', target: BASE_VUS },   // hold
      { duration: '30s', target: PEAK_VUS },  // push into contention
      { duration: '1m', target: PEAK_VUS },
      { duration: '30s', target: 0 },
    ];

export const options = {
  scenarios: {
    steady: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: STAGES,
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // Placement is a write plus an outbox insert; anything past a quarter second
    // at p95 means lock contention is no longer amortised.
    'order_placement_duration': ['p(95)<250', 'p(99)<600'],
    // 429s are excluded from this by the response callback above.
    'http_req_failed': ['rate<0.01'],
  },
};

// 429 is the rate limiter doing its job, not a transport failure. Without this
// every throttled request would be counted against http_req_failed and the
// error rate would say nothing useful.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 429));

// Two identities, because the gateway's limiter buckets per JWT subject: a
// single-user run would measure the rate limiter rather than the system.
// Supplied by `make load` from the cache that `make login` populates.
export function setup() {
  // Tokens are minted outside k6 by the authorization-code login and passed in
  // as env vars. k6 cannot drive a browser flow, and re-authenticating per
  // iteration would load-test Asgardeo rather than this system.
  const tokens = [__ENV.FRESHCHAIN_TOKEN_1, __ENV.FRESHCHAIN_TOKEN_2].filter(Boolean);

  if (tokens.length === 0) {
    throw new Error(
      'no tokens supplied. Run `make login` first; `make load` passes the cached ' +
        'tokens in as FRESHCHAIN_TOKEN_1 and FRESHCHAIN_TOKEN_2.',
    );
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
  const sku = SKUS[Math.floor(Math.random() * SKUS.length)];

  group('place order', () => {
    const body = JSON.stringify({
      warehouseId: WAREHOUSE,
      allowPartial: true,
      lines: [{ sku, qty: Math.ceil(Math.random() * 5) }],
    });

    const res = http.post(`${GATEWAY}/api/v1/orders`, body, { headers, timeout: '10s' });
    placementLatency.add(res.timings.duration);

    // 429 is the gateway's rate limiter working as designed, not an error.
    if (res.status === 429) {
      rateLimited.add(1);
      return;
    }

    check(res, { 'accepted for allocation (202)': (r) => r.status === 202 });

    const orderId = res.status === 202 ? res.json('order.orderId') : null;
    if (!orderId) {
      rejected.add(1);
      return;
    }

    // A client polls for the allocation outcome; one read stands in for that.
    const read = http.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers, timeout: '10s' });
    check(read, { 'order is readable': (r) => r.status === 200 });
  });

  group('check availability', () => {
    const res = http.get(
      `${GATEWAY}/api/v1/inventory/${sku}/availability?warehouseId=${WAREHOUSE}`,
      { headers, timeout: '10s' },
    );
    check(res, { 'availability is readable': (r) => r.status === 200 || r.status === 429 });
  });

  sleep(Math.random() * 0.5);
}
