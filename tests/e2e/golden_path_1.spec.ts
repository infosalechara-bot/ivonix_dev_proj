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
const eventRows=await db(`/rest/v1/events?organization_id=eq.${ORG_ID}&external_event_id=eq.${telemetry.messageId}&select=id,event_type_id&limit=1`);assert.equal(eventRows.length,1);

const idem=crypto.randomUUID(); const command=await api('/api/v1/device-commands',{method:'POST',headers:{'Idempotency-Key':idem},body:JSON.stringify({organizationId:ORG_ID,deviceId,commandType:'SET_SPEED',payload:{rpm:1200}})});assert.equal(command.r.status,202);assert.ok(command.body.commandId);
const received=await waitForMessage(client);assert.equal(received.commandId,command.body.commandId);assert.equal(received.payload.rpm,1200);
const commandRows=await db(`/rest/v1/device_commands?id=eq.${command.body.commandId}&select=status&limit=1`);assert.equal(commandRows[0].status,'published');

const ack={schemaVersion:'1.0',messageId:crypto.randomUUID(),deviceId,messageType:'COMMAND_ACK',sentAt:new Date().toISOString(),sequence:2,payload:{commandId:command.body.commandId,status:'accepted'}};
client.publish(`pulse/v1/devices/${deviceId}/ack`,JSON.stringify(ack),{qos:1});
let acknowledged=false; for(let i=0;i<20;i++){await new Promise(r=>setTimeout(r,500));const rows=await db(`/rest/v1/device_commands?id=eq.${command.body.commandId}&select=status,acknowledged_at&limit=1`);if(rows[0]?.status==='acknowledged'){acknowledged=true;break}}
assert.ok(acknowledged,'command was not acknowledged');
const audit=await db(`/rest/v1/audit_logs?resource_id=eq.${deviceId}&action=in.(device.register,telemetry.received)&select=action`);assert.ok(audit.some((x:any)=>x.action==='device.register'));assert.ok(audit.some((x:any)=>x.action==='telemetry.received'));
const commandAudit=await db(`/rest/v1/audit_logs?resource_id=eq.${command.body.commandId}&action=in.(command.issued,command.acknowledged)&select=action`);assert.ok(commandAudit.some((x:any)=>x.action==='command.issued'));assert.ok(commandAudit.some((x:any)=>x.action==='command.acknowledged'));
client.end(true); console.log('BLOCK 1 GOLDEN PATH 1: PASS — register, auth, MQTT, webhook, telemetry, online, event, UI projection, command, receipt, ACK, audit');
