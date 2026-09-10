import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: Number(__ENV.PULSE_API_VUS || 500),
  duration: __ENV.PULSE_API_DURATION || '2m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

export default function () {
  const base = __ENV.PULSE_BASE_URL || 'http://localhost:8081';
  const token = __ENV.PULSE_ACCESS_TOKEN;
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = http.get(`${base}/api/v1/health`, { headers });
  check(response, { 'read endpoint succeeds': (r) => r.status >= 200 && r.status < 300 });
}
