import React,{useEffect,useState} from 'react';
import {supabase} from '../supabaseClient';

export default function EventMonitor({organizationId}){
 const [events,setEvents]=useState([]);
 useEffect(()=>{
  if(!organizationId)return;
  let mounted=true;
  supabase.from('events').select('id,event_type_id,source_service,event_time,payload,correlation_id,version').eq('organization_id',organizationId).order('event_time',{ascending:false}).limit(100).then(({data})=>{if(mounted)setEvents(data||[]);});
  const channel=supabase.channel(`pulse-event-bus-${organizationId}`).on('postgres_changes',{event:'INSERT',schema:'public',table:'events',filter:`organization_id=eq.${organizationId}`},({new:ev})=>setEvents(p=>[ev,...p].slice(0,100))).subscribe();
  return()=>{mounted=false;supabase.removeChannel(channel);};
 },[organizationId]);
 return <section><h2>PULSE EVENT BUS</h2><div>{events.map(e=><article key={e.id}><strong>{e.source_service}</strong> · {new Date(e.event_time).toLocaleString()}<div>{JSON.stringify(e.payload).slice(0,240)}</div>{e.correlation_id&&<small>correlation: {e.correlation_id}</small>}</article>)}</div></section>;
}
