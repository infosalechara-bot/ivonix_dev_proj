import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.PULSE_BASE_URL || 'http://localhost:8081';
const TOKEN = __ENV.PULSE_TEST_JWT || '';
export const options = {
  scenarios: {
    concurrent_reads: {
      executor: 'constant-vus',
      vus: Number(__ENV.PULSE_READ_VUS || 500),
      duration: __ENV.PULSE_READ_DURATION || '2m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get(`${BASE}/api/v1/security/health`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
    tags: { workload: 'api-read-authenticated' },
  });
  check(res, {
    'authenticated read succeeds': r => r.status === 200 && r.json('authenticated') === true,
  });
}
