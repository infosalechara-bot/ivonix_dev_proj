import assert from 'node:assert/strict';
import mqtt from 'mqtt';

const API = process.env.PULSE_E2E_API_URL ?? 'http://localhost:8081';
const MQTT = process.env.PULSE_E2E_MQTT_URL ?? 'mqtt://localhost:1883';
const USER_TOKEN = process.env.PULSE_E2E_USER_TOKEN;
const ORG_ID = process.env.PULSE_E2E_ORG_ID;
const SUPABASE_URL = process.env.PULSE_E2E_SUPABASE_URL;
const SUPABASE_KEY = process.env.PULSE_E2E_SUPABASE_SERVICE_ROLE_KEY;

for (const [name,value] of Object.entries({PULSE_E2E_USER_TOKEN:USER_TOKEN,PULSE_E2E_ORG_ID:ORG_ID,PULSE_E2E_SUPABASE_URL:SUPABASE_URL,PULSE_E2E_SUPABASE_SERVICE_ROLE_KEY:SUPABASE_KEY})) assert.ok(value, `${name} is required`);

async function api(path:string, init:RequestInit={}) {
  const r=await fetch(`${API}${path}`,{...init,headers:{'content-type':'application/json','authorization':`Bearer ${USER_TOKEN}`,...(init.headers??{})}});
  const text=await r.text(); let body:any; try{body=JSON.parse(text)}catch{body=text}
  return {r,body};
}
async function db(path:string){
  const r=await fetch(`${SUPABASE_URL}${path}`,{headers:{apikey:SUPABASE_KEY!,authorization:`Bearer ${SUPABASE_KEY!}`}});
  assert.equal(r.status,200,`DB query failed: ${path}`); return r.json();
}
function waitForMessage(client:any,timeout=10000){return new Promise<any>((resolve,reject)=>{const timer=setTimeout(()=>{client.removeAllListeners('message');reject(new Error('MQTT message timeout'))},timeout);client.once('message',(_topic:any,payload:any)=>{clearTimeout(timer);resolve(JSON.parse(payload.toString()))})})}
async function waitForCommandStatus(commandId:string,status:string){for(let i=0;i<20;i++){const rows=await db(`/rest/v1/device_commands?id=eq.${commandId}&select=status&limit=1`);if(rows[0]?.status===status)return;await new Promise(r=>setTimeout(r,500));}throw new Error(`command ${commandId} did not reach ${status}`)}

const activation=await api('/api/v1/key/activation-codes',{method:'POST',body:JSON.stringify({organizationId:ORG_ID,ttlMinutes:10,maxUses:1,scopes:['key:use']})});
assert.equal(activation.r.status,200); assert.ok(activation.body.code);
const activated=await api('/api/v1/key/activate',{method:'POST',body:JSON.stringify({code:activation.body.code,deviceName:`golden-${Date.now()}`,deviceType:'industrial'})});
assert.equal(activated.r.status,200); const {deviceId,deviceToken}=activated.body; assert.ok(deviceId&&deviceToken);

const authProbe=await fetch(`${API}/api/v1/key/device/me`,{headers:{'X-PULSE-DEVICE-TOKEN':deviceToken}}); assert.equal(authProbe.status,200);
const client=mqtt.connect(MQTT,{username:deviceId,password:deviceToken,clientId:deviceId,clean:true,protocolVersion:5});
await new Promise((resolve,reject)=>{client.once('connect',resolve);client.once('error',reject)});
await new Promise((resolve,reject)=>client.subscribe(`pulse/v1/devices/${deviceId}/commands`,{qos:1},(e:any)=>e?reject(e):resolve(undefined)));

const telemetry={schemaVersion:'1.0',messageId:crypto.randomUUID(),deviceId,messageType:'TELEMETRY',sentAt:new Date().toISOString(),sequence:1,payload:{temperature:42.5,pressure:7.2}};
client.publish(`pulse/v1/devices/${deviceId}/telemetry`,JSON.stringify(telemetry),{qos:1});

let live:any; for(let i=0;i<20;i++){await new Promise(r=>setTimeout(r,500));const x=await api(`/api/v1/devices/${deviceId}/live`);if(x.r.ok&&x.body.latestTelemetry?.id){live=x.body;break}}
assert.ok(live,'live telemetry was not projected'); assert.equal(live.device.status,'online'); assert.equal(live.latestTelemetry.data.temperature,42.5);
const telemetryRows=await db(`/rest/v1/device_telemetry?device_id=eq.${deviceId}&select=id,data&order=id.desc&limit=1`);assert.equal(telemetryRows.length,1);
const eventRows=await db(`/rest/v1/events?organization_id=eq.${ORG_ID}&external_event_id=eq.${telemetry.messageId}&select=id,event_type_id,organization_id,payload,canonical_envelope&limit=1`);assert.equal(eventRows.length,1); assert.equal(eventRows[0].organization_id,ORG_ID);
const canonical=eventRows[0].canonical_envelope;
assert.ok(canonical,'event did not persist a canonical PULSE envelope');
assert.match(canonical.eventId,/^[0-9A-HJKMNP-TV-Z]{26}$/);
assert.equal(canonical.eventVersion,1); assert.equal(canonical.envelopeVersion,1); assert.equal(canonical.organizationId,ORG_ID); assert.equal(canonical.aggregateType,'Machine'); assert.equal(canonical.aggregateId,deviceId);
assert.equal(canonical.payload.deviceId,deviceId); assert.equal(canonical.payload.messageId,telemetry.messageId); assert.match(canonical.eventType,/^[a-z][a-z0-9]*(\.[a-z0-9]+)+$/);
assert.match(canonical.occurredAt,/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/); assert.match(canonical.signature,/^sha256=[0-9a-f]{64}$/);

// Exact message replay is idempotent: the same messageId must not create another telemetry row.
client.publish(`pulse/v1/devices/${deviceId}/telemetry`,JSON.stringify(telemetry),{qos:1});
await new Promise(r=>setTimeout(r,1000));
const replayRows=await db(`/rest/v1/device_telemetry?device_id=eq.${deviceId}&select=id&order=id.desc&limit=2`);assert.equal(replayRows.length,1,'exact telemetry replay created a duplicate row');

// The authenticated device identity is authoritative; a message cannot claim another device.
const beforeIdentityMismatch=await db(`/rest/v1/device_telemetry?device_id=eq.${deviceId}&select=id`);
const identityMismatch={...telemetry,messageId:crypto.randomUUID(),deviceId:crypto.randomUUID(),sequence:2,payload:{temperature:88}};
client.publish(`pulse/v1/devices/${deviceId}/telemetry`,JSON.stringify(identityMismatch),{qos:1});
await new Promise(r=>setTimeout(r,1000));
const afterIdentityMismatch=await db(`/rest/v1/device_telemetry?device_id=eq.${deviceId}&select=id`);assert.equal(afterIdentityMismatch.length,beforeIdentityMismatch.length,'authenticated device accepted a mismatched deviceId');

const idem=crypto.randomUUID();
const commandBody=JSON.stringify({organizationId:ORG_ID,deviceId,commandType:'SET_SPEED',payload:{rpm:1200}});
const [commandA,commandB]=await Promise.all([
  api('/api/v1/device-commands',{method:'POST',headers:{'Idempotency-Key':idem},body:commandBody}),
  api('/api/v1/device-commands',{method:'POST',headers:{'Idempotency-Key':idem},body:commandBody})
]);
assert.equal(commandA.r.status,202); assert.equal(commandB.r.status,202); assert.ok(commandA.body.commandId); assert.equal(commandA.body.commandId,commandB.body.commandId,'concurrent idempotent requests created different commands');
const command=commandA;
const received=await waitForMessage(client);assert.equal(received.commandId,command.body.commandId);assert.equal(received.payload.rpm,1200);
await waitForCommandStatus(command.body.commandId,'published');
const commandRows=await db(`/rest/v1/device_commands?id=eq.${command.body.commandId}&select=status,organization_id&limit=1`);assert.equal(commandRows[0].status,'published');assert.equal(commandRows[0].organization_id,ORG_ID);

// A caller cannot substitute an organization it is not a member of, even with a valid session.
const unauthorizedOrg=crypto.randomUUID();
const denied=await api('/api/v1/device-commands',{method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({organizationId:unauthorizedOrg,deviceId,commandType:'SET_SPEED',payload:{rpm:1300}})});
assert.ok([401,403].includes(denied.r.status),`cross-tenant command was not denied: HTTP ${denied.r.status}`);
const deniedRows=await db(`/rest/v1/device_commands?organization_id=eq.${unauthorizedOrg}&device_id=eq.${deviceId}&select=id&limit=1`);assert.equal(deniedRows.length,0,'cross-tenant command was persisted');

const ack={schemaVersion:'1.0',messageId:crypto.randomUUID(),deviceId,messageType:'COMMAND_ACK',sentAt:new Date().toISOString(),sequence:2,payload:{commandId:command.body.commandId,status:'accepted'}};
client.publish(`pulse/v1/devices/${deviceId}/ack`,JSON.stringify(ack),{qos:1});
await waitForCommandStatus(command.body.commandId,'acknowledged');

// A different message at an already-consumed sequence must be rejected and must not write telemetry.
const beforeStale=await db(`/rest/v1/device_telemetry?device_id=eq.${deviceId}&select=id`);
const stale={schemaVersion:'1.0',messageId:crypto.randomUUID(),deviceId,messageType:'TELEMETRY',sentAt:new Date().toISOString(),sequence:1,payload:{temperature:99,pressure:99}};
client.publish(`pulse/v1/devices/${deviceId}/telemetry`,JSON.stringify(stale),{qos:1});
await new Promise(r=>setTimeout(r,1000));
const afterStale=await db(`/rest/v1/device_telemetry?device_id=eq.${deviceId}&select=id`);assert.equal(afterStale.length,beforeStale.length,'stale telemetry sequence was accepted');
const staleEvent=await db(`/rest/v1/events?organization_id=eq.${ORG_ID}&external_event_id=eq.${stale.messageId}&select=id&limit=1`);assert.equal(staleEvent.length,0,'stale telemetry emitted an event');

const audit=await db(`/rest/v1/audit_logs?resource_id=eq.${deviceId}&action=in.(device.register,telemetry.received)&select=action`);assert.ok(audit.some((x:any)=>x.action==='device.register'));assert.ok(audit.some((x:any)=>x.action==='telemetry.received'));
const commandAudit=await db(`/rest/v1/audit_logs?resource_id=eq.${command.body.commandId}&action=in.(command.issued,command.acknowledged)&select=action`);assert.ok(commandAudit.some((x:any)=>x.action==='command.issued'));assert.ok(commandAudit.some((x:any)=>x.action==='command.acknowledged'));
client.end(true); console.log('BLOCK 1 GOLDEN PATH 1: PASS — registration, auth, MQTT, telemetry, online projection, canonical event envelope, replay rejection, device identity isolation, concurrent command idempotency, tenant authorization, command receipt, ACK, audit');
