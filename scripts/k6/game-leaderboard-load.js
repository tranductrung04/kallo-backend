import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const tokens = JSON.parse(open(__ENV.TOKENS_FILE || './k6-tokens.json')); // requires 100 verified-user tokens

export const options = {
  scenarios: {
    leaderboard: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  if (!Array.isArray(tokens) || tokens.length === 0) {
    throw new Error('TOKENS_FILE must contain verified-user access tokens');
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const response = http.get(`${baseUrl}/api/leaderboard/friends?metric=POINTS`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(response, {
    'leaderboard is 200': value => value.status === 200,
    'leaderboard envelope is successful': value => value.json('success') === true,
  });
  sleep(1);
}
