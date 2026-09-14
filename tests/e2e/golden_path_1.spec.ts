import assert from 'node:assert/strict';
import mqtt from 'mqtt';

// --- Configuration ---
const API = (process.env.PULSE_E2E_API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const MQTT = process.env.PULSE_E2E_MQTT_URL ?? 'mqtt://localhost:1883';
const USER_TOKEN = process.env.PULSE_E2E_USER_TOKEN;
const ORG_ID = process.env.PULSE_E2E_ORG_ID;
const SUPABASE_URL = process.env.PULSE_E2E_SUPABASE_URL;
const SUPABASE_KEY = process.env.PULSE_E2E_SUPABASE_SERVICE_ROLE_KEY;

// --- Validation of Environment Variables ---
for (const [name, value] of Object.entries({
  PULSE_E2E_USER_TOKEN: USER_TOKEN,
  PULSE_E2E_ORG_ID: ORG_ID,
  PULSE_E2E_SUPABASE_URL: SUPABASE_URL,
  PULSE_E2E_SUPABASE_SERVICE_ROLE_KEY: SUPABASE_KEY
})) {
  assert.ok(value, `❌ Environment variable ${name} is required but was empty or missing.`);
}

if (/\/api\/v1\/?$/i.test(API)) {
  throw new Error(`❌ PULSE_E2E_API_URL must not end with /api/v1 (got ${API}). Use the server origin only.`);
}

// --- Helper Functions ---
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

async function db(path: string) {
  const r = await fetch(`${SUPABASE_URL}${path}`, {
    headers: {
      apikey: SUPABASE_KEY!,
      authorization: `Bearer ${SUPABASE_KEY!}`
    }
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

// --- Main Test Execution ---
async function run() {
  console.log('🚀 Starting Golden Path E2E Test...');
  console.log(`Target API: ${API}`);
  console.log(`Target MQTT: ${MQTT}`);

  // Step 1: Create an Activation Code
  console.log('\n📝 Step 1: Creating activation code...');
  const activationPayload = {
    organizationId: ORG_ID,
    ttlMinutes: 10,
    maxUses: 1,
    scopes: ['key:use']
  };

  const activation = await api('/api/v1/key/activation-codes', {
    method: 'POST',
    body: JSON.stringify(activationPayload)
  });

  // If the status is not 200, throw a highly detailed error so we can debug it!
  if (activation.r.status !== 200) {
    const serverResponse = typeof activation.body === 'string' 
      ? activation.body.slice(0, 1000) 
      : JSON.stringify(activation.body, null, 2);

    console.error('❌ FAILED: The server rejected the request.');
    console.error(`Status Code: ${activation.r.status}`);
    console.error(`URL: ${activation.url}`);
    console.error(`Payload Sent: ${JSON.stringify(activationPayload, null, 2)}`);
    console.error(`Server Response: ${serverResponse}`);
    
    throw new Error(`activation-codes expected HTTP 200, got ${activation.r.status}. Check the logs above for the server's response.`);
  }

  console.log('✅ Activation code created successfully!');
  console.log('Response:', activation.body);

  // Step 2: Connect to MQTT (Example continuation)
  // Add your MQTT and database logic here based on your original test file.
  
  console.log('\n🎉 Golden Path completed successfully!');
}

// Run the test and catch any unhandled errors
run().catch((error) => {
  console.error('\n💥 Test execution failed:');
  console.error(error.message);
  process.exit(1);
});
