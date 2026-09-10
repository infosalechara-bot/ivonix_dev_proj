import hmac, os, traceback
from pathlib import Path
from fastapi import FastAPI, BackgroundTasks, HTTPException, Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel
import joblib, numpy as np, pandas as pd, requests
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor, IsolationForest

app=FastAPI(title='PULSE AI Studio Training',version='1.1')
SUPABASE_URL=os.environ.get('SUPABASE_URL','').rstrip('/')
SUPABASE_KEY=os.environ.get('SUPABASE_SERVICE_ROLE_KEY','')
SERVICE_SECRET=os.environ.get('PULSE_AI_TRAINING_SERVICE_SECRET','')
MODEL_ROOT=Path(os.environ.get('MODEL_ROOT','/models')).resolve(); MODEL_ROOT.mkdir(parents=True,exist_ok=True)
bearer=HTTPBearer(auto_error=False)
if not SUPABASE_URL or not SUPABASE_KEY or not SERVICE_SECRET:
    raise RuntimeError('Training worker requires Supabase credentials and PULSE_AI_TRAINING_SERVICE_SECRET')

class TrainingRequest(BaseModel):
    organization_id:str
    model_id:str
    job_id:str

def require_service(credentials:HTTPAuthorizationCredentials|None=Depends(bearer)):
    if credentials is None or credentials.scheme.lower()!='bearer' or not hmac.compare_digest(credentials.credentials,SERVICE_SECRET):
        raise HTTPException(401,'Unauthorized')
    return True

def headers(): return {'apikey':SUPABASE_KEY,'Authorization':f'Bearer {SUPABASE_KEY}','Content-Type':'application/json'}
def get(path,params=None):
    r=requests.get(SUPABASE_URL+'/rest/v1/'+path,headers=headers(),params=params,timeout=15); r.raise_for_status(); return r.json()
def patch(job_id,organization_id,status,**fields):
    body={'status':status,**fields}; r=requests.patch(SUPABASE_URL+'/rest/v1/ai_training_jobs',headers=headers(),params={'id':f'eq.{job_id}','organization_id':f'eq.{organization_id}'},json=body,timeout=15); r.raise_for_status()
def scoped_model(model_id,organization_id):
    rows=get('ai_models',{'id':f'eq.{model_id}','organization_id':f'eq.{organization_id}'})
    if len(rows)!=1: raise ValueError('Training model not found')
    return rows[0]
def scoped_job(job_id,organization_id,model_id):
    rows=get('ai_training_jobs',{'id':f'eq.{job_id}','organization_id':f'eq.{organization_id}','select':'id,organization_id,model_id'})
    if len(rows)!=1 or rows[0].get('model_id')!=model_id: raise ValueError('Training job/model mismatch')
    return rows[0]
def safe_artifact(model_id):
    path=(MODEL_ROOT/model_id/'model.joblib').resolve()
    if MODEL_ROOT not in path.parents: raise ValueError('Artifact path escapes model root')
    return path

def run(req:TrainingRequest):
    try:
        scoped_job(req.job_id,req.organization_id,req.model_id)
        model=scoped_model(req.model_id,req.organization_id)
        patch(req.job_id,req.organization_id,'running',started_at=pd.Timestamp.utcnow().isoformat())
        dataset=get('ai_datasets',{'id':f'eq.{model["dataset_id"]}','organization_id':f'eq.{req.organization_id}'})
        if len(dataset)!=1: raise ValueError('Training dataset not found')
        dataset=dataset[0]; fmt=(dataset.get('format') or 'csv').lower(); path=dataset.get('storage_path')
        if fmt!='csv': raise ValueError('CSV is the only registered trainer format')
        source=Path(path or '').resolve()
        if not source.is_file(): raise ValueError('Dataset is unavailable to training worker')
        df=pd.read_csv(source)
        target='target' if 'target' in df.columns else None
        if not target: raise ValueError('CSV must contain a target column named target')
        X=df.drop(columns=[target]); y=df[target]; X=pd.get_dummies(X).replace([np.inf,-np.inf],np.nan).fillna(0)
        typ=model['model_type']; hp=model.get('hyperparameters') or {}; n=int(hp.get('n_estimators',100))
        if not 1<=n<=2000: raise ValueError('n_estimators out of bounds')
        if typ=='classification': estimator=RandomForestClassifier(n_estimators=n,random_state=42)
        elif typ=='regression': estimator=RandomForestRegressor(n_estimators=n,random_state=42)
        elif typ=='anomaly': estimator=IsolationForest(n_estimators=n,random_state=42,contamination=hp.get('contamination','auto'))
        else: raise ValueError('Unsupported trainer model type')
        estimator.fit(X,y if typ!='anomaly' else None)
        artifact=safe_artifact(req.model_id); artifact.parent.mkdir(parents=True,exist_ok=True)
        joblib.dump({'model':estimator,'columns':list(X.columns)},artifact)
        metrics={'rows':int(len(df)),'features':int(X.shape[1]),'framework':'sklearn','model_type':typ}
        patch(req.job_id,req.organization_id,'completed',completed_at=pd.Timestamp.utcnow().isoformat(),metrics=metrics,model_artifact_path=str(artifact))
    except Exception:
        try: patch(req.job_id,req.organization_id,'failed',completed_at=pd.Timestamp.utcnow().isoformat(),metrics={'error':'training_failed'})
        except Exception: pass

@app.get('/health')
def health(): return {'status':'ok'}
@app.post('/train')
def train(req:TrainingRequest,bg:BackgroundTasks,_service:bool=Depends(require_service)):
    try:
        scoped_job(req.job_id,req.organization_id,req.model_id); scoped_model(req.model_id,req.organization_id)
    except Exception as exc: raise HTTPException(409,'Training request rejected') from exc
    bg.add_task(run,req)
    return {'status':'training started','job_id':req.job_id}
