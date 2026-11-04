// Нагрузочный сценарий BehaviorGuard (k6).
// Запуск: k6 run bg-load.js -e BG_SDK_KEY=... -e BG_SERVER_KEY=... -e BG_ADMIN_USER=admin -e BG_ADMIN_PASS=...
// Ключи и пароль берутся из вывода dev-профиля backend. Не коммитьте их.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BG_BASE || 'http://localhost:8080';
const SDK = __ENV.BG_SDK_KEY;
const SERVER = __ENV.BG_SERVER_KEY;
const ADMIN_USER = __ENV.BG_ADMIN_USER || 'admin';
const ADMIN_PASS = __ENV.BG_ADMIN_PASS;
const USERS = 20;

export const options = {
  scenarios: {
    sdk_flow: {
      executor: 'ramping-vus', exec: 'sdkFlow', startVUs: 1,
      stages: [{ duration: '20s', target: 10 }, { duration: '50s', target: 10 }, { duration: '10s', target: 0 }],
    },
    risk_assess: { executor: 'constant-vus', exec: 'riskFlow', vus: 4, duration: '80s' },
    dashboard: { executor: 'constant-vus', exec: 'dashboardFlow', vus: 2, duration: '80s' },
  },
  thresholds: {
    'http_req_duration{name:session_create}': ['p(95)<300'],
    'http_req_duration{name:events_batch}': ['p(95)<300'],
    'http_req_duration{name:session_finish}': ['p(95)<300'],
    'http_req_duration{name:risk_assess}': ['p(95)<800'],
    'http_req_duration{name:dashboard_overview}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const json = { 'Content-Type': 'application/json' };

function sdkHeaders() { return { headers: { ...json, 'X-BG-Api-Key': SDK } }; }
function serverHeaders() { return { headers: { ...json, 'X-BG-Api-Key': SERVER } }; }

function createSession(userId, tag) {
  const res = http.post(`${BASE}/api/v1/sessions`,
    JSON.stringify({ userId, deviceType: 'MOBILE', sdkVersion: '0.1.0' }),
    { ...sdkHeaders(), tags: { name: 'session_create' } });
  check(res, { 'session 201': (r) => r.status === 201 }, { tag });
  return res.status === 201 ? res.json('id') : null;
}

// Реалистичный пакет из 40 событий: тап, свайп, скролл, текст, экран, редко вставка.
function eventsBatch(sid) {
  const now = new Date().toISOString();
  const ev = [];
  for (let i = 0; i < 20; i++) ev.push({ eventType: 'TAP', eventTime: now, payload: { durationMs: 80 + Math.random() * 120, intervalMs: 300 + Math.random() * 600 } });
  for (let i = 0; i < 8; i++) ev.push({ eventType: 'SWIPE', eventTime: now, payload: { distancePx: 200 + Math.random() * 300, durationMs: 150 + Math.random() * 100, avgSpeedPxS: 800 + Math.random() * 400, direction: 'LEFT' } });
  for (let i = 0; i < 6; i++) ev.push({ eventType: 'SCROLL', eventTime: now, payload: { distancePx: 300 + Math.random() * 300, durationMs: 700 + Math.random() * 400, avgSpeedPxS: 500 + Math.random() * 200, direction: 'DOWN' } });
  ev.push({ eventType: 'TEXT_INPUT', eventTime: now, payload: { fieldId: 'transfer.amount', keyCount: 15, keyIntervalAvgMs: 200 + Math.random() * 50, keyIntervalStdMs: 30, charsPerMinute: 180 + Math.random() * 40, backspaceCount: 1 } });
  ev.push({ eventType: 'SCREEN_VIEW', eventTime: now, payload: { screen: 'transfer', dwellMs: 3000 + Math.random() * 3000 } });
  return http.post(`${BASE}/api/v1/events/batch`, JSON.stringify({ sessionId: sid, events: ev }),
    { ...sdkHeaders(), tags: { name: 'events_batch' } });
}

export function setup() {
  const runId = Date.now().toString(36);
  const users = [];
  for (let u = 0; u < USERS; u++) {
    const userId = `load-${runId}-${u}`;
    for (let s = 0; s < 5; s++) {
      const sid = createSession(userId, 'setup');
      if (sid) {
        eventsBatch(sid);
        http.post(`${BASE}/api/v1/sessions/${sid}/finish`, null, { ...sdkHeaders(), tags: { name: 'session_finish' } });
      }
    }
    users.push(userId);
  }
  const login = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }), { headers: json });
  return { users, token: login.json('accessToken') };
}

export function sdkFlow() {
  const userId = `sdk-${__VU}-${__ITER}-${Date.now().toString(36)}`;
  const sid = createSession(userId, 'sdk');
  if (sid) {
    check(eventsBatch(sid), { 'batch 202': (r) => r.status === 202 });
    http.post(`${BASE}/api/v1/sessions/${sid}/finish`, null, { ...sdkHeaders(), tags: { name: 'session_finish' } });
  }
  sleep(0.2);
}

export function riskFlow(data) {
  const userId = data.users[(__VU + __ITER) % data.users.length];
  const sid = createSession(userId, 'risk');
  if (sid) {
    eventsBatch(sid);
    const res = http.post(`${BASE}/api/v1/risk/assess`, JSON.stringify({ sessionId: sid, context: { action: 'TRANSFER', newRecipient: false } }),
      { ...serverHeaders(), tags: { name: 'risk_assess' } });
    check(res, { 'assess 200': (r) => r.status === 200 });
  }
  sleep(0.2);
}

export function dashboardFlow(data) {
  const res = http.get(`${BASE}/api/v1/dashboard/overview?hours=24`,
    { headers: { Authorization: `Bearer ${data.token}` }, tags: { name: 'dashboard_overview' } });
  check(res, { 'overview 200': (r) => r.status === 200 });
  sleep(0.5);
}
