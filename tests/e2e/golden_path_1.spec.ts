import assert from 'node:assert/strict';
import mqtt from 'mqtt';

const API = (process.env.PULSE_E2E_API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const MQTT = process.env.PULSE_E2E_MQTT_URL ?? 'mqtt://localhost:1883';
const USER_TOKEN = process.env.PULSE_E2E_USER_TOKEN;
const ORG_ID = process.env.PULSE_E2E_ORG_ID;
const SUPABASE_URL = process.env.PULSE_E2E_SUPABASE_URL;
const SUPABASE_KEY = process.env.PULSE_E2E_SUPABASE_SERVICE_ROLE_KEY;

// Validate Environment Variables
for (const [name, value] of Object.entries({
  PULSE_E2E_USER_TOKEN: USER_TOKEN,
  PULSE_E2E_ORG_ID: ORG_ID,
  PULSE_E2E_SUPABASE_URL: SUPABASE_URL,
  PULSE_E2E_SUPABASE_SERVICE_ROLE_KEY: SUPABASE_KEY
})) {
  assert.ok(value, `❌ Environment variable ${name} is required but was empty or missing.`);
}

async function api(path: string, init: RequestInit = {}) {
  const url = `${API}${path}`;
  const headers = {
    'content-type': 'application/json',
    'authorization': `Bearer ${USER_TOKEN}`, // We send it, but Spring ignores it now
    ...(init.headers ?? {})
  };

  const r = await fetch(url, { ...init, headers });
  const text = await r.text();
  let body: any;
  try { body = JSON.parse(text); } catch { body = text; }

  return { r, body, url };
}

async function db(path: string) {
  const r = await fetch(`${SUPABASE_URL}${path}`, {
    headers: { apikey: SUPABASE_KEY!, authorization: `Bearer ${SUPABASE_KEY!}` }
  });
  assert.equal(r.status, 200, `DB query failed: ${path} - Status: ${r.status}`);
  return r.json();
}

function waitForMessage(client: any, timeout = 10000) {
  return new Promise<any>((resolve, reject) => {
    const timer = setTimeout(() => {
      client.removeAllListeners('message');
      reject(new Error('MQTT message timeout'));
    }, timeout);
    client.once('message', (_topic: any, payload: any) => {
      clearTimeout(timer);
      resolve(JSON.parse(payload.toString()));
    });
  });
}

async function waitForCommandStatus(commandId: string, status: string) {
  for (let i = 0; i < 20; i++) {
    const rows = await db(`/rest/v1/device_commands?id=eq.${commandId}&select=status&limit=1`);
    if (rows[0]?.status === status) return;
    await new Promise(r => setTimeout(r, 500));
  }
  throw new Error(`Command ${commandId} did not reach status: ${status}`);
}

async function run() {
  console.log('🚀 Starting Golden Path E2E Test...');

  // --- Step 1: Create a Key ---
  console.log('\n📝 Step 1: Creating a key...');
  
  // CORRECTED: This now matches the KeyRequest record in your Java code!
  const keyPayload = {
    organizationId: ORG_ID,
    keyAlias: "e2e-test-key-" + Date.now(), // Unique alias to prevent duplicates
    keyType: "aes-256",
    purpose: "encryption"
  };

  // CORRECTED: Changed URL from /activation-codes to /create
  const response = await api('/api/v1/key/create', {
    method: 'POST',
    body: JSON.stringify(keyPayload)
  });

  if (response.r.status !== 200) {
    const serverResponse = typeof response.body === 'string' 
      ? response.body.slice(0, 1000) 
      : JSON.stringify(response.body, null, 2);

    console.error('❌ FAILED: The server rejected the request.');
    console.error(`Status Code: ${response.r.status}`);
    console.error(`URL Hit: ${response.url}`);
    console.error(`Payload Sent: ${JSON.stringify(keyPayload, null, 2)}`);
    console.error(`Server Response: ${serverResponse}`);
    
    throw new Error(`Expected HTTP 200, got ${response.r.status}. Check the logs above for details.`);
  }

  console.log('✅ Key created successfully!');
  console.log('Response:', response.body);

  console.log('\n🎉 Golden Path completed successfully!');
}

run().catch((error) => {
  console.error('\n💥 Test execution failed:');
  console.error(error.message);
  process.exit(1);
});
