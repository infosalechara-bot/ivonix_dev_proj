const API = (process.env.PULSE_E2E_API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const ORG_ID = process.env.PULSE_E2E_ORG_ID;
const USER_TOKEN = process.env.PULSE_E2E_USER_TOKEN;

async function run() {
  console.log('🚀 Starting Golden Path E2E Test...');
  console.log(`API Target: ${API}`);
  console.log(`Org ID: ${ORG_ID}`);

  const payload = {
    organizationId: ORG_ID,
    keyAlias: "e2e-test-key-" + Date.now(),
    keyType: "aes-256",
    purpose: "encryption"
  };

  console.log('\n📝 Sending Payload:', JSON.stringify(payload));

  const res = await fetch(`${API}/api/v1/key/create`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'authorization': `Bearer ${USER_TOKEN}`
    },
    body: JSON.stringify(payload)
  });

  const responseText = await res.text();
  
  console.log('\n📥 SERVER RESPONSE:');
  console.log(`Status Code: ${res.status}`);
  console.log(`Body: ${responseText}`);

  if (res.status !== 200) {
    console.error('\n❌ Test Failed because status was not 200.');
    process.exit(1); 
  }

  console.log('\n✅ Test Passed!');
}

run().catch(err => {
  console.error('💥 Crash:', err);
  process.exit(1);
});
