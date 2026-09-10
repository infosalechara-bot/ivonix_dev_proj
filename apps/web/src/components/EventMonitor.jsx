import React,{useEffect,useState} from 'react';
import {pulseApi} from '../lib/pulseApi';

export default function EventMonitor({organizationId}){
 const [events,setEvents]=useState([]),[error,setError]=useState('');
 useEffect(()=>{
  if(!organizationId)return;
  let mounted=true;
  setError('');
  pulseApi(`/event-bus/events?organizationId=${encodeURIComponent(organizationId)}&limit=100`).then(data=>{if(mounted)setEvents(data||[])}).catch(e=>{if(mounted)setError(e.message)});
  return()=>{mounted=false;};
 },[organizationId]);
 return <section className="panel"><h2>PULSE EVENT BUS</h2>{error&&<p role="alert">{error}</p>}<div>{events.map(e=><article key={e.id}><strong>{e.source_service}</strong> · {new Date(e.event_time).toLocaleString()}<div>{JSON.stringify(e.payload).slice(0,240)}</div>{e.correlation_id&&<small>correlation: {e.correlation_id}</small>}</article>)}</div></section>;
}
