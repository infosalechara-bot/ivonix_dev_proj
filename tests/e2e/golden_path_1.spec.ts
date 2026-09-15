import assert from 'node:assert/strict';

const API = (process.env.PULSE_E2E_API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const USER_TOKEN = process.env.PULSE_E2E_USER_TOKEN;
const ORG_ID = process.env.PULSE_E2E_ORG_ID;

// Validate Environment Variables
for (const [name, value] of Object.entries({
  PULSE_E2E_USER_TOKEN: USER_TOKEN,
  PULSE_E2E_ORG_ID: ORG_ID
})) {
  assert.ok(value, `❌ Environment variable ${name} is required but was empty or missing.`);
}

async function api(path: string, init: RequestInit = {}) {
  const url = `${API}${path}`;
  const headers = {
    'content-type': 'application/json',
    'authorization': `Bearer ${USER_TOKEN}`,
    ...(init.headers ?? {})
  };

  const r = await fetch(url, { ...init, headers });
  const text = await r.text();
  let body: any;
  try { body = JSON.parse(text); } catch { body = text; }

  return { r, body, url };
}

async function run() {
  console.log('🚀 Starting Golden Path E2E Test...');

  const keyPayload = {
    organizationId: ORG_ID,
    keyAlias: "e2e-test-key-" + Date.now(),
    keyType: "aes-256",
    purpose: "encryption"
  };

  console.log('\n📝 Sending payload:', JSON.stringify(keyPayload));

  // This is the correct endpoint based on your KeyController
  const response = await api('/api/v1/key/create', {
    method: 'POST',
    body: JSON.stringify(keyPayload)
  });

  // ⚠️ THIS IS THE DETAILED ERROR LOGGING YOU WERE MISSING
  if (response.r.status !== 200) {
    const serverResponse = typeof response.body === 'string' 
      ? response.body.slice(0, 1000) 
      : JSON.stringify(response.body, null, 2);

    console.error('❌ FAILED: The server rejected the request.');
    console.error(`Status Code: ${response.r.status}`);
    console.error(`URL Hit: ${response.url}`);
    console.error(`Server Response: ${serverResponse}`);
    
    process.exit(1);
  }

  console.log('✅ Key created successfully!');
  console.log('Response:', response.body);
}

run().catch((error) => {
  console.error('\n💥 Test execution failed:', error.message);
  process.exit(1);
});
