// Поиск предела: поток SDK (сессия, пакет событий, завершение) с фиксированной частотой.
// Запуск: k6 run -e RATE=100 bg-capacity.js ...  (RATE — итераций в секунду)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BG_BASE || 'http://localhost:8080';
const SDK = __ENV.BG_SDK_KEY;
const RATE = parseInt(__ENV.RATE || '100', 10);
const DURATION = __ENV.DURATION || '40s';

export const options = {
  scenarios: {
    capacity: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 300,
    },
  },
  thresholds: {
    'http_req_duration{name:events_batch}': ['p(95)<300'],
    'http_req_duration{name:session_create}': ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

const H = { headers: { 'Content-Type': 'application/json', 'X-BG-Api-Key': SDK } };

export default function () {
  const create = http.post(`${BASE}/api/v1/sessions`,
    JSON.stringify({ userId: `cap-${__VU}-${__ITER}-${Date.now().toString(36)}`, deviceType: 'MOBILE', sdkVersion: '0.1.0' }),
    { ...H, tags: { name: 'session_create' } });
  check(create, { 'session 201': (r) => r.status === 201 });
  if (create.status !== 201) return;
  const sid = create.json('id');
  const now = new Date().toISOString();
  const ev = [];
  for (let i = 0; i < 40; i++) ev.push({ eventType: 'TAP', eventTime: now, payload: { durationMs: 100, intervalMs: 400 } });
  const batch = http.post(`${BASE}/api/v1/events/batch`, JSON.stringify({ sessionId: sid, events: ev }),
    { ...H, tags: { name: 'events_batch' } });
  check(batch, { 'batch 202': (r) => r.status === 202 });
  http.post(`${BASE}/api/v1/sessions/${sid}/finish`, null, { ...H, tags: { name: 'session_finish' } });
}
