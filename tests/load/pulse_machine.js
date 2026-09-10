import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.PULSE_BASE_URL || 'http://localhost:8081';
const TOKEN = __ENV.PULSE_TEST_JWT || '';

export const options = {
  scenarios: {
    sustained_telemetry: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.PULSE_MSG_RATE || 10000),
      timeUnit: '1s',
      duration: __ENV.PULSE_LOAD_DURATION || '5m',
      preAllocatedVUs: Number(__ENV.PULSE_PREALLOCATED_VUS || 500),
      maxVUs: Number(__ENV.PULSE_MAX_VUS || 2000),
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<250', 'p(99)<800'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const body = JSON.stringify({
    deviceId: __ENV.PULSE_TEST_DEVICE_ID || '00000000-0000-0000-0000-000000000000',
    eventType: 'telemetry.v1',
    eventId: `${__VU}-${__ITER}-${Date.now()}`,
    payload: { temperature: 42.1, vibration: 0.12 },
  });
  const res = http.post(`${BASE}/api/v1/security/events`, body, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` },
    tags: { workload: 'machine-telemetry' },
  });
  check(res, { 'request accepted': r => r.status >= 200 && r.status < 300 });
  sleep(0.001);
}
