from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional
import os, requests

app=FastAPI(title='PULSE VERITAS', version='1.0')

class AnalysisRequest(BaseModel):
    evidence_id:str
    session_id:str
    evidence_type:str
    file_path:Optional[str]=None
    text_content:Optional[str]=None

# VERITAS deliberately reports observable communication signals, not a truth/deception verdict.
def analyze_text(text):
    text=text or ''
    signals=[]
    low=text.lower()
    for term in ('maybe','perhaps','i think','i guess','not sure'):
        if term in low: signals.append({'start_time':None,'end_time':None,'marker_type':'linguistic_hedging','score':None,'description':f'Possible hedging phrase: {term}'})
    return signals, 'Text contains linguistic features that may merit human review. These features are not evidence of deception.'

def analyze_audio(path):
    # Hook for deterministic signal extraction (pitch/pause/energy) when an approved model is deployed.
    return [], 'Audio analysis is limited to observable signal features; no deception probability is produced.'

def analyze_video(path):
    # Hook for observable facial/temporal feature extraction; no truthfulness inference.
    return [], 'Video analysis is limited to observable temporal/facial signal changes; no deception probability is produced.'

def supabase_headers():
    key=os.getenv('SUPABASE_SERVICE_ROLE_KEY');
    if not key: raise RuntimeError('SUPABASE_SERVICE_ROLE_KEY is not configured')
    return {'apikey':key,'Authorization':f'Bearer {key}','Content-Type':'application/json'}

def post(path,payload):
    base=os.getenv('SUPABASE_URL');
    if not base: raise RuntimeError('SUPABASE_URL is not configured')
    r=requests.post(f'{base}/rest/v1/{path}',json=payload,headers=supabase_headers(),timeout=15); r.raise_for_status(); return r.json()

def run(req):
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
def analyze(req:AnalysisRequest):
    try: return {'status':'completed','analysis_id':run(req)}
    except Exception as e: raise HTTPException(500,str(e))
