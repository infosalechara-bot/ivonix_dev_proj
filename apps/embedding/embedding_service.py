from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import hashlib, io, math, os, requests
import numpy as np
from PIL import Image

app=FastAPI(title='PULSE Embedding Service', version='1.0')
DIM=1536
MAX_BYTES=10*1024*1024

class EmbeddingRequest(BaseModel):
    type: str=Field(pattern='^(face|image|voice|text)$')
    file_url: str|None=None
    text: str|None=None

def fixed_dimension(values):
    v=np.asarray(values,dtype=np.float32).reshape(-1)
    if len(v)>DIM: v=v[:DIM]
    if len(v)<DIM: v=np.pad(v,(0,DIM-len(v)))
    n=float(np.linalg.norm(v))
    return (v/n if n else v).tolist()

def download(url):
    if not url or not url.startswith('https://'): raise HTTPException(400,'file_url must use HTTPS')
    try:
        r=requests.get(url,timeout=(3,15),stream=True,headers={'User-Agent':'PULSE-Embedding/1.0'})
        r.raise_for_status(); chunks=[]; total=0
        for chunk in r.iter_content(65536):
            total+=len(chunk)
            if total>MAX_BYTES: raise HTTPException(413,'Input exceeds size limit')
            chunks.append(chunk)
        return b''.join(chunks)
    except HTTPException: raise
    except requests.RequestException as e: raise HTTPException(400,f'Unable to fetch input: {e}')

def deterministic_text(text):
    # Stable fallback only; production semantic matching should load a configured model.
    raw=hashlib.sha512(text.encode()).digest(); out=[]
    for i in range(DIM): out.append(((raw[i%len(raw)]/255.0)*2.0)-1.0)
    return fixed_dimension(out)

@app.get('/health')
def health(): return {'status':'ok','dimension':DIM}

@app.post('/generate')
def generate(req:EmbeddingRequest):
    if req.type=='text':
        if not req.text or not req.text.strip(): raise HTTPException(400,'Text required')
        return {'embedding':deterministic_text(req.text)}
    data=download(req.file_url)
    if req.type in ('image','face'):
        try:
            image=Image.open(io.BytesIO(data)).convert('RGB')
            # Deterministic visual fingerprint keeps the API operational without silently
            # pretending that a generic image hash is a biometric model embedding.
            small=np.asarray(image.resize((32,32)),dtype=np.float32).flatten()/255.0
            return {'embedding':fixed_dimension(small),'model':'visual-fingerprint-v1','note':'Configure a validated face/image model before biometric production use.'}
        except Exception as e: raise HTTPException(400,f'Invalid image: {e}')
    if req.type=='voice':
        return {'embedding':fixed_dimension(np.frombuffer(data,dtype=np.uint8).astype(np.float32)),'model':'audio-fingerprint-v1','note':'Configure a validated speaker model before biometric production use.'}
    raise HTTPException(400,'Unsupported type')
