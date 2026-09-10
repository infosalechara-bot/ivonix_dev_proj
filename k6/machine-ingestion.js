import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    sustained_ingestion: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.PULSE_MSG_RATE || 10000),
      timeUnit: '1s',
      duration: __ENV.PULSE_LOAD_DURATION || '5m',
      preAllocatedVUs: Number(__ENV.PULSE_PREALLOCATED_VUS || 100),
      maxVUs: Number(__ENV.PULSE_MAX_VUS || 1000),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250', 'p(99)<800'],
  },
};

export default function () {
  const base = __ENV.PULSE_BASE_URL || 'http://localhost:8081';
  const token = __ENV.PULSE_DEVICE_TOKEN;
  const payload = JSON.stringify({
    deviceId: __ENV.PULSE_DEVICE_ID || 'load-test-device',
    eventId: `${__VU}-${__ITER}`,
    eventType: 'telemetry',
    occurredAt: new Date().toISOString(),
    payload: { loadTest: true, sequence: `${__VU}-${__ITER}` },
  });
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = http.post(`${base}/api/v1/device-gateway/events`, payload, { headers });
  check(response, { 'ingestion accepted': (r) => r.status >= 200 && r.status < 300 });
}
