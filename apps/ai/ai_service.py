from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import Any

app=FastAPI(title='PULSE AI Diagnostic Service',version='1.0')
class DiagnosisRequest(BaseModel):
    machine_domain:str|None=None
    telemetry:dict[str,Any]={}
    evidence:list[dict[str,Any]]=[]

@app.get('/health')
def health(): return {'status':'ok'}

@app.post('/diagnose')
def diagnose(req:DiagnosisRequest):
    t=req.telemetry
    if isinstance(t.get('temperature'),(int,float)) and t['temperature']>85: return {'probable_fault':'Overheating','confidence':0.92,'recommended_actions':['Inspect cooling system','Verify operating load'],'do_not_disassemble':True,'evidence':['temperature > 85']}
    if isinstance(t.get('vibration_rms'),(int,float)) and t['vibration_rms']>1.5: return {'probable_fault':'Bearing degradation','confidence':0.85,'recommended_actions':['Inspect bearings','Check alignment'],'do_not_disassemble':True,'evidence':['vibration_rms > 1.5']}
    if req.evidence: return {'probable_fault':'Acoustic/evidence anomaly','confidence':0.70,'recommended_actions':['Review supplied evidence','Follow approved diagnostic procedure'],'do_not_disassemble':True,'evidence':['external evidence present']}
    return {'probable_fault':'No rule-based fault detected','confidence':0.10,'recommended_actions':['Continue telemetry collection'],'do_not_disassemble':True,'evidence':[]}
