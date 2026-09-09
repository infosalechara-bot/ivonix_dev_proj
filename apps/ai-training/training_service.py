import os, tempfile, traceback
from pathlib import Path
import joblib, numpy as np, pandas as pd, requests
from fastapi import FastAPI, BackgroundTasks, HTTPException
from pydantic import BaseModel
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor, IsolationForest

app=FastAPI(title='PULSE AI Studio Training')
SUPABASE_URL=os.environ.get('SUPABASE_URL','').rstrip('/')
SUPABASE_KEY=os.environ.get('SUPABASE_SERVICE_ROLE_KEY','')
MODEL_ROOT=Path(os.environ.get('MODEL_ROOT','/models')); MODEL_ROOT.mkdir(parents=True,exist_ok=True)
class TrainingRequest(BaseModel): model_id:str; job_id:str

def headers():
    if not SUPABASE_URL or not SUPABASE_KEY: raise RuntimeError('Supabase training credentials are not configured')
    return {'apikey':SUPABASE_KEY,'Authorization':f'Bearer {SUPABASE_KEY}','Content-Type':'application/json'}
def get(path,params=None):
    r=requests.get(SUPABASE_URL+'/rest/v1/'+path,headers=headers(),params=params,timeout=15); r.raise_for_status(); return r.json()
def patch(job,status,**fields):
    body={'status':status,**fields}; r=requests.patch(SUPABASE_URL+f'/rest/v1/ai_training_jobs?id=eq.{job}',headers=headers(),json=body,timeout=15); r.raise_for_status()
def run(model_id,job_id):
    try:
        patch(job_id,'running',started_at=pd.Timestamp.utcnow().isoformat())
        model=get('ai_models',{'id':f'eq.{model_id}'})[0]
        dataset=get('ai_datasets',{'id':f'eq.{model["dataset_id"]}'})[0]
        fmt=(dataset.get('format') or 'csv').lower(); path=dataset.get('storage_path')
        if fmt!='csv': raise ValueError('Initial internal trainer supports CSV datasets; other formats require registered preprocessors')
        if not path or not Path(path).is_file(): raise ValueError('Dataset storage_path must resolve to a local worker-readable file')
        df=pd.read_csv(path)
        target=df.attrs.get('target') or ('target' if 'target' in df.columns else None)
        if not target: raise ValueError('CSV must contain a target column named target')
        X=df.drop(columns=[target]); y=df[target]
        X=pd.get_dummies(X).replace([np.inf,-np.inf],np.nan).fillna(0)
        typ=model['model_type']; hp=model.get('hyperparameters') or {}
        if typ=='classification': estimator=RandomForestClassifier(n_estimators=int(hp.get('n_estimators',100)),random_state=42)
        elif typ=='regression': estimator=RandomForestRegressor(n_estimators=int(hp.get('n_estimators',100)),random_state=42)
        elif typ=='anomaly': estimator=IsolationForest(n_estimators=int(hp.get('n_estimators',100)),random_state=42,contamination=hp.get('contamination','auto'))
        else: raise ValueError(f'Unsupported initial trainer model_type: {typ}')
        estimator.fit(X,y if typ!='anomaly' else None)
        artifact=MODEL_ROOT/model_id; artifact.mkdir(parents=True,exist_ok=True); joblib.dump({'model':estimator,'columns':list(X.columns)},artifact/'model.joblib')
        metrics={'rows':int(len(df)),'features':int(X.shape[1]),'framework':'sklearn','model_type':typ}
        patch(job_id,'completed',completed_at=pd.Timestamp.utcnow().isoformat(),metrics=metrics,model_artifact_path=str(artifact/'model.joblib'))
    except Exception as e:
        patch(job_id,'failed',completed_at=pd.Timestamp.utcnow().isoformat(),metrics={'error':str(e),'traceback':traceback.format_exc()[:4000]})

@app.get('/health')
def health(): return {'status':'ok'}
@app.post('/train')
def train(req:TrainingRequest,bg:BackgroundTasks): bg.add_task(run,req.model_id,req.job_id); return {'status':'training started','job_id':req.job_id}
