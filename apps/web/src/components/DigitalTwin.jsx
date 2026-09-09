import React,{useEffect,useState} from 'react';

const API=import.meta.env.VITE_API_BASE_URL||'http://localhost:8081/api/v1';
async function api(path,options={}){const t=localStorage.getItem('pulse_access_token');const r=await fetch(API+path,{...options,headers:{'Content-Type':'application/json',...(t?{Authorization:`Bearer ${t}`}:{})}});if(!r.ok)throw Error(await r.text());return r.status===204?null:r.json()}

export default function DigitalTwin(){
 const [deviceId,setDeviceId]=useState(''),[twins,setTwins]=useState([]),[predictions,setPredictions]=useState([]),[model,setModel]=useState('thermal'),[name,setName]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function load(){if(!deviceId)return;try{setError('');const [t,p]=await Promise.all([api(`/digital-twin/twins?deviceId=${deviceId}`),api(`/digital-twin/devices/${deviceId}/predictions`)]);setTwins(t);setPredictions(p)}catch(e){setError(e.message)}}
 useEffect(()=>{load()},[deviceId]);
 async function create(){try{setBusy(true);setError('');await api('/digital-twin/twins',{method:'POST',body:JSON.stringify({deviceId,name:name||'Machine Twin',simulationModel:model,parameters:{ambient_temp:20,cooling_coefficient:0.1,heat_source:5}})});setName('');await load()}catch(e){setError(e.message)}finally{setBusy(false)}}
 async function simulate(id){try{setBusy(true);setError('');await api(`/digital-twin/twins/${id}/simulate`,{method:'POST',body:JSON.stringify(model==='thermal'?{current_temp:30}:{})});await load()}catch(e){setError(e.message)}finally{setBusy(false)}}
 return <section className="panel"><div className="panel-title"><span className="dot"/><h2>PULSE DIGITAL TWIN · DTW-021</h2></div>
  <p>Live virtual replicas for telemetry-driven simulation and predictive maintenance.</p>
  <div className="actions"><input value={deviceId} onChange={e=>setDeviceId(e.target.value)} placeholder="Device UUID"/><input value={name} onChange={e=>setName(e.target.value)} placeholder="Twin name"/><select value={model} onChange={e=>setModel(e.target.value)}><option value="thermal">Thermal</option><option value="vibration">Vibration</option><option value="energy">Energy</option></select><button disabled={!deviceId||busy} onClick={create}>Create twin</button><button disabled={!deviceId||busy} onClick={load}>Refresh</button></div>
  {error&&<pre className="error">{error}</pre>}
  <div className="result"><strong>Twins</strong>{twins.length===0?<p>No digital twins for this device.</p>:twins.map(t=><div key={t.id} className="result"><b>{t.name}</b> · {t.simulation_model}<button disabled={busy} onClick={()=>simulate(t.id)}>Run simulation</button></div>)}</div>
  <div className="result"><strong>Predicted failures</strong>{predictions.length===0?<p>No predicted failures.</p>:predictions.map(p=><div key={p.id}><b>{p.failure_type}</b> · {(Number(p.probability)*100).toFixed(0)}% · {p.time_horizon||'unspecified'}<br/>{p.recommended_action||''}</div>)}</div>
 </section>
}
