import React,{useEffect,useState} from 'react';

const API=import.meta.env.VITE_API_BASE_URL||'http://localhost:8081/api/v1';
async function api(path,options={}){
  const t=localStorage.getItem('pulse_access_token');
  const r=await fetch(API+path,{...options,headers:{'Content-Type':'application/json',...(t?{Authorization:`Bearer ${t}`}:{})}});
  if(!r.ok)throw Error(await r.text());
  return r.status===204?null:r.json();
}

export default function FounderConsole(){
  const [profile,setProfile]=useState(null),[messages,setMessages]=useState([]),[text,setText]=useState('');
  const [action,setAction]=useState(''),[pending,setPending]=useState(null),[decisionToken,setDecisionToken]=useState('');
  const [error,setError]=useState(''),[busy,setBusy]=useState(false);
  const load=async()=>{try{setError('');const p=await api('/founder/profile');setProfile(p);setMessages(await api('/founder/messages'));}catch(e){setError('Founder authorization unavailable. '+e.message);setProfile(null);}};
  useEffect(()=>{load();},[]);
  const send=async()=>{if(!text.trim())return;setBusy(true);try{const m=await api('/founder/messages',{method:'POST',body:JSON.stringify({messageText:text,messageType:'text',urgency:'normal'})});setMessages(x=>[m,...x]);setText('');}catch(e){setError(e.message)}finally{setBusy(false)}};
  const request=async()=>{if(!action.trim())return;setBusy(true);try{const r=await api('/founder/confirmations',{method:'POST',body:JSON.stringify({action})});setPending(r);setDecisionToken(r.confirmationToken);setAction('');setMessages(await api('/founder/messages'));}catch(e){setError(e.message)}finally{setBusy(false)}};
  const decide=async approve=>{if(!pending||!decisionToken)return;setBusy(true);try{await api(`/founder/confirmations/${pending.id}/decision`,{method:'POST',body:JSON.stringify({token:decisionToken,approve})});setPending(null);setDecisionToken('');setMessages(await api('/founder/messages'));}catch(e){setError(e.message)}finally{setBusy(false)}};
  if(!profile)return <div style={{background:'#050505',color:'#ddd',minHeight:'100vh',padding:'3rem',fontFamily:'ui-monospace,monospace'}}><h1>PULSE FOUNDER</h1><p>Access denied. This surface is not available to non-executive users.</p>{error&&<pre style={{color:'#f88',whiteSpace:'pre-wrap'}}>{error}</pre>}</div>;
  return <main style={{background:'#050505',color:'#ddd',minHeight:'100vh',padding:'2rem',fontFamily:'ui-monospace,monospace'}}>
    <header style={{display:'flex',justifyContent:'space-between',alignItems:'center',borderBottom:'1px solid #333',paddingBottom:'1rem'}}><div><small style={{color:'#6f6'}}>FND-026 / EXECUTIVE COMMAND LAYER</small><h1 style={{margin:'.35rem 0'}}>PULSE FOUNDER</h1><div>{profile.codename} · trust: {profile.trustLevel}</div></div><div style={{border:'1px solid #396',padding:'.6rem .8rem',color:'#7f7'}}>FOUNDER AUTHORIZED</div></header>
    {error&&<pre style={{color:'#f88',whiteSpace:'pre-wrap'}}>{error}</pre>}
    <section style={{marginTop:'1.5rem',display:'grid',gridTemplateColumns:'2fr 1fr',gap:'1rem'}}>
      <div style={{border:'1px solid #333',padding:'1rem'}}><h2>Private channel</h2><div style={{height:420,overflowY:'auto',background:'#090909',padding:'1rem'}}>{messages.map(m=><div key={m.id} style={{marginBottom:'.9rem',borderLeft:`3px solid ${m.urgency==='critical'?'#f44':'#396'}`,paddingLeft:'.7rem'}}><b>{m.sender==='founder'?'YOU':'PULSE AGENT'}</b> <small>{new Date(m.createdAt).toLocaleString()}</small><div>{m.messageText}</div>{m.urgency==='critical'&&<strong style={{color:'#f55'}}>CRITICAL</strong>}</div>)}</div><div style={{display:'flex',gap:'.5rem',marginTop:'.8rem'}}><input style={{flex:1,background:'#111',color:'#ddd',border:'1px solid #555',padding:'.7rem'}} value={text} onChange={e=>setText(e.target.value)} placeholder="Message PULSE..." onKeyDown={e=>e.key==='Enter'&&send()}/><button disabled={busy||!text.trim()} onClick={send}>Send</button></div></div>
      <div style={{border:'1px solid #333',padding:'1rem'}}><h2>Critical action gate</h2><p style={{color:'#aaa'}}>Every critical action requires an explicit founder decision. Tokens are hashed at rest and are single-use.</p><textarea style={{width:'100%',minHeight:130,background:'#111',color:'#ddd',border:'1px solid #555',padding:'.7rem',boxSizing:'border-box'}} value={action} onChange={e=>setAction(e.target.value)} placeholder="Describe the action requiring approval"/><button disabled={busy||!action.trim()} onClick={request} style={{marginTop:'.7rem'}}>Request confirmation</button>{pending&&<div style={{marginTop:'1rem',border:'1px solid #a66',padding:'1rem'}}><strong>Pending confirmation</strong><p>{pending.requestedAction}</p><small>Token is shown once and never stored in plaintext.</small><input type="password" value={decisionToken} onChange={e=>setDecisionToken(e.target.value)} style={{width:'100%',marginTop:'.7rem',boxSizing:'border-box',background:'#111',color:'#ddd',padding:'.6rem'}}/><div style={{display:'flex',gap:'.5rem',marginTop:'.7rem'}}><button disabled={busy||!decisionToken} onClick={()=>decide(true)}>APPROVE</button><button disabled={busy||!decisionToken} onClick={()=>decide(false)}>DENY</button></div></div>}</div>
    </section>
    <footer style={{marginTop:'1rem',color:'#777',fontSize:'.85rem'}}>Privacy boundary: PULSE provides defensive controls and authorization gates; it does not promise invisibility from telecoms, cameras, sensors, or lawful monitoring.</footer>
  </main>;
}
