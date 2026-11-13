import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const tokens = JSON.parse(open(__ENV.TOKENS_FILE || './k6-tokens.json'));

export const options = {
  scenarios: {
    notifications: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      exec: 'notifications',
    },
    syncPush: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      startTime: '35s',
      exec: 'syncPush',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  if (!Array.isArray(tokens) || tokens.length < 100) {
    throw new Error('TOKENS_FILE must contain at least 100 verified-user access tokens');
  }
  return { tokens };
}

function auth(data) {
  return {
    headers: {
      Authorization: `Bearer ${data.tokens[(__VU - 1) % data.tokens.length]}`,
    },
  };
}

export function notifications(data) {
  const response = http.get(`${baseUrl}/api/notifications?limit=20`, auth(data));
  check(response, { 'notifications is 200': r => r.status === 200 });
  // Keep each user below 120 requests/minute and leave headroom for retries.
  sleep(1);
}

export function syncPush(data) {
  const operations = Array.from({ length: 100 }, (_, i) => ({
    clientUuid: `00000000-0000-4000-8000-${String(__VU).padStart(8, '0')}${String(__ITER * 100 + i).padStart(4, '0')}`,
    entityType: 'WATER_LOG',
    operation: 'UPSERT',
    updatedAt: new Date().toISOString(),
    data: { amountMl: 250 },
  }));
  const response = http.post(
    `${baseUrl}/api/sync/push`,
    JSON.stringify({ operations }),
    { headers: { ...auth(data).headers, 'Content-Type': 'application/json' } },
  );
  let payload;
  try {
    payload = response.json();
  } catch (_) {
    payload = null;
  }
  check(response, {
    'sync push is 200': r => r.status === 200,
    'sync push returns 100 results': () => payload?.data?.results?.length === 100,
  });
  // Keep each user below 30 sync-push requests/minute.
  sleep(3);
}

// ReminderEngine has no HTTP endpoint. Measure its 100-user pass with ReminderEngineTest
// or an application-level timer; do not manufacture a fake HTTP metric here.
