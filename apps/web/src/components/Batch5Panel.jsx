import React,{useEffect,useState} from 'react';
import {pulseApi} from '../lib/pulseApi';

export function AcademyPanel({organizationId}){
 const[courses,setCourses]=useState([]),[err,setErr]=useState('');
 useEffect(()=>{if(!organizationId)return; pulseApi(`/academy/courses?organizationId=${encodeURIComponent(organizationId)}`).then(setCourses).catch(e=>setErr(e.message));},[organizationId]);
 return <section className="panel" aria-labelledby="academy-title"><h2 id="academy-title">PULSE Academy</h2>{err&&<p role="alert">{err}</p>}{courses.map(c=><article key={c.id}><h3>{c.title}</h3><p>{c.summary}</p><button onClick={()=>pulseApi(`/academy/courses/${encodeURIComponent(c.id)}/enroll?organizationId=${encodeURIComponent(organizationId)}`,{method:'POST'}).catch(e=>setErr(e.message))}>Enroll</button></article>)}</section>;
}

export function MeetPanel({organizationId}){
 const[name,setName]=useState(''),[room,setRoom]=useState(null),[err,setErr]=useState('');
 async function create(){try{setRoom(await pulseApi('/meet/rooms',{method:'POST',body:JSON.stringify({organizationId,name,options:{maxParticipants:20,recordingEnabled:true,transcriptEnabled:true}})}));}catch(e){setErr(e.message);}}
 return <section className="panel" aria-labelledby="meet-title"><h2 id="meet-title">PULSE Meet</h2><label>Room name<input value={name} onChange={e=>setName(e.target.value)}/></label><button disabled={!name||!organizationId} onClick={create}>Create room</button>{room&&<p>Room: {room.roomId}</p>}{err&&<p role="alert">{err}</p>}</section>;
}

export function GlobalPanel(){
 const[locale,setLocale]=useState('en'),[dir,setDir]=useState('ltr');
 useEffect(()=>{pulseApi(`/i18n/${encodeURIComponent(locale)}/direction`).then(x=>{setDir(x.direction);document.documentElement.lang=locale;document.documentElement.dir=x.direction;}).catch(()=>{});},[locale]);
 return <section className="panel" aria-labelledby="global-title"><h2 id="global-title">PULSE Global</h2><label>Language<select value={locale} onChange={e=>setLocale(e.target.value)}><option value="en">English</option><option value="fr">Français</option><option value="ar">العربية</option><option value="sw">Kiswahili</option><option value="zh">简体中文</option><option value="es">Español</option></select></label><p>Direction: {dir}</p></section>;
}

export default function Batch5Panel(props){return <><AcademyPanel {...props}/><GlobalPanel/><MeetPanel {...props}/></>;
