import React, { useEffect, useState } from 'react';

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api/v1';
const token = () => localStorage.getItem('pulse_access_token');
const headers = () => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` });

export default function RecoveryDashboard({ organizationId }) {
  const [jobs, setJobs] = useState([]);
  const [selected, setSelected] = useState(null);
  const [items, setItems] = useState([]);
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({ jobType: 'file', targetPath: '', targetTable: '', targetIdentifier: '', deviceId: '' });

  const loadJobs = async () => {
    if (!organizationId) return;
    const r = await fetch(`${API}/reclaim/organizations/${organizationId}/jobs`, { headers: headers() });
    if (r.ok) setJobs(await r.json());
  };
  useEffect(() => { loadJobs(); const id = setInterval(loadJobs, 5000); return () => clearInterval(id); }, [organizationId]);

  const selectJob = async (id) => {
    setSelected(id);
    const r = await fetch(`${API}/reclaim/jobs/${id}/items`, { headers: headers() });
    if (r.ok) setItems(await r.json());
  };

  const createJob = async (e) => {
    e.preventDefault(); setMessage('');
    const payload = { organizationId, jobType: form.jobType, targetPath: form.targetPath || null, targetTable: form.targetTable || null, targetIdentifier: form.targetIdentifier || null, deviceId: form.deviceId || null };
    const r = await fetch(`${API}/reclaim/jobs`, { method: 'POST', headers: headers(), body: JSON.stringify(payload) });
    if (!r.ok) { setMessage(await r.text()); return; }
    setMessage('Recovery job queued.'); setForm({ ...form, targetPath: '', targetTable: '', targetIdentifier: '', deviceId: '' }); loadJobs();
  };

  return <section style={{ padding: 20 }}>
    <h2>PULSE RECLAIM · REC-018</h2>
    <p>Authorized organizational recovery with administrator authorization and audit logging.</p>
    <form onSubmit={createJob} style={{ display: 'grid', gap: 8, maxWidth: 620 }}>
      <select value={form.jobType} onChange={e => setForm({ ...form, jobType: e.target.value })}>
        <option value="file">File</option><option value="message">Message</option><option value="database">Database</option><option value="system">System</option>
      </select>
      {form.jobType === 'database' ? <input placeholder="Target table" value={form.targetTable} onChange={e => setForm({ ...form, targetTable: e.target.value })} /> : <input placeholder="Mounted forensic source path" value={form.targetPath} onChange={e => setForm({ ...form, targetPath: e.target.value })} required />}
      <input placeholder="Device UUID (optional)" value={form.deviceId} onChange={e => setForm({ ...form, deviceId: e.target.value })} />
      <input placeholder="Identifier (optional)" value={form.targetIdentifier} onChange={e => setForm({ ...form, targetIdentifier: e.target.value })} />
      <button type="submit">Start authorized recovery</button>
    </form>
    {message && <p>{message}</p>}
    <div style={{ display: 'flex', gap: 24, marginTop: 24 }}>
      <div style={{ flex: 1 }}><h3>Recovery Jobs</h3>{jobs.map(j => <button key={j.id} onClick={() => selectJob(j.id)} style={{ display: 'block', width: '100%', textAlign: 'left', marginBottom: 6, padding: 8 }}>{j.job_type} · {j.status} · {j.progress}%</button>)}</div>
      <div style={{ flex: 2 }}><h3>Recovered Items</h3>{selected && <table><thead><tr><th>Type</th><th>Location</th><th>Confidence</th><th>Data</th></tr></thead><tbody>{items.map(i => <tr key={i.id}><td>{i.item_type}</td><td>{i.original_location}</td><td>{i.confidence ?? '—'}</td><td>{i.recovered_data}</td></tr>)}</tbody></table>}</div>
    </div>
  </section>;
}
