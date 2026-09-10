from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel
from typing import Optional
import hmac, os, requests

app=FastAPI(title='PULSE VERITAS', version='1.1')
SERVICE_SECRET=os.getenv('PULSE_VERITAS_SERVICE_SECRET','')
if not SERVICE_SECRET: raise RuntimeError('PULSE_VERITAS_SERVICE_SECRET is required')

class AnalysisRequest(BaseModel):
    evidence_id:str
    session_id:str
    evidence_type:str
    file_path:Optional[str]=None
    text_content:Optional[str]=None

def analyze_text(text):
    text=text or ''; signals=[]; low=text.lower()
    for term in ('maybe','perhaps','i think','i guess','not sure'):
        if term in low: signals.append({'start_time':None,'end_time':None,'marker_type':'linguistic_hedging','score':None,'description':f'Possible hedging phrase: {term}'})
    return signals, 'Text contains linguistic features that may merit human review. These features are not evidence of deception.'
def analyze_audio(path): return [], 'Audio analysis is limited to observable signal features; no deception probability is produced.'
def analyze_video(path): return [], 'Video analysis is limited to observable temporal/facial signal changes; no deception probability is produced.'
def supabase_headers():
    key=os.getenv('SUPABASE_SERVICE_ROLE_KEY')
    if not key: raise RuntimeError('SUPABASE_SERVICE_ROLE_KEY is not configured')
    return {'apikey':key,'Authorization':f'Bearer {key}','Content-Type':'application/json'}
def get(path,params):
    base=os.getenv('SUPABASE_URL')
    if not base: raise RuntimeError('SUPABASE_URL is not configured')
    r=requests.get(f'{base}/rest/v1/{path}',params=params,headers=supabase_headers(),timeout=15); r.raise_for_status(); return r.json()
def post(path,payload):
    base=os.getenv('SUPABASE_URL')
    if not base: raise RuntimeError('SUPABASE_URL is not configured')
    r=requests.post(f'{base}/rest/v1/{path}',json=payload,headers=supabase_headers(),timeout=15); r.raise_for_status(); return r.json()
def validate_ownership(req):
    rows=get('veritas_evidence',{'id':f'eq.{req.evidence_id}','session_id':f'eq.{req.session_id}','select':'id,session_id,evidence_type'})
    if len(rows)!=1 or rows[0].get('evidence_type')!=req.evidence_type: raise PermissionError('Evidence/session mismatch')
    sessions=get('veritas_sessions',{'id':f'eq.{req.session_id}','select':'id,organization_id'})
    if len(sessions)!=1: raise PermissionError('Session not found')
def run(req):
    validate_ownership(req)
    if req.evidence_type=='text': markers,summary=analyze_text(req.text_content)
    elif req.evidence_type=='audio': markers,summary=analyze_audio(req.file_path)
    elif req.evidence_type=='video': markers,summary=analyze_video(req.file_path)
    else: raise ValueError('unsupported evidence type')
    rows=post('veritas_analysis',{'evidence_id':req.evidence_id,'session_id':req.session_id,'confidence':0.0,'summary':summary})
    analysis_id=rows[0]['id']
    for m in markers: post('veritas_markers',dict(m,analysis_id=analysis_id))
    return analysis_id
@app.get('/health')
def health(): return {'status':'ok','mode':'assistive-signal-analysis'}
@app.post('/analyze')
def analyze(req:AnalysisRequest, x_pulse_veritas_secret:Optional[str]=Header(default=None)):
    if x_pulse_veritas_secret is None or not hmac.compare_digest(x_pulse_veritas_secret,SERVICE_SECRET): raise HTTPException(401,'Unauthorized')
    try: return {'status':'completed','analysis_id':run(req)}
    except PermissionError as e: raise HTTPException(403,str(e))
    except Exception as e: raise HTTPException(500,str(e))
