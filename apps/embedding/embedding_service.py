from fastapi import FastAPI, HTTPException, Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
import hashlib, io, ipaddress, math, os, socket, requests
import numpy as np
from PIL import Image

app=FastAPI(title='PULSE Embedding Service', version='1.1')
DIM=1536
MAX_BYTES=10*1024*1024
SERVICE_SECRET=os.environ.get('PULSE_EMBEDDING_SERVICE_SECRET','')
bearer=HTTPBearer(auto_error=False)
if not SERVICE_SECRET:
    raise RuntimeError('PULSE_EMBEDDING_SERVICE_SECRET is required')

class EmbeddingRequest(BaseModel):
    type: str=Field(pattern='^(face|image|voice|text)$')
    file_url: str|None=None
    text: str|None=None

def require_service(credentials: HTTPAuthorizationCredentials|None=Depends(bearer)):
    import hmac
    if credentials is None or credentials.scheme.lower()!='bearer' or not hmac.compare_digest(credentials.credentials,SERVICE_SECRET):
        raise HTTPException(401,'Unauthorized')
    return True

def _safe_host(host: str):
    try:
        addresses=socket.getaddrinfo(host,None,type=socket.SOCK_STREAM)
    except socket.gaierror:
        raise HTTPException(400,'Unable to resolve input host')
    for item in addresses:
        ip=ipaddress.ip_address(item[4][0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved or ip.is_unspecified:
            raise HTTPException(400,'Input host is not publicly routable')

def download(url):
    from urllib.parse import urlparse
    if not url or not url.startswith('https://'): raise HTTPException(400,'file_url must use HTTPS')
    parsed=urlparse(url)
    if not parsed.hostname: raise HTTPException(400,'file_url host is required')
    _safe_host(parsed.hostname)
    try:
        r=requests.get(url,timeout=(3,15),stream=True,allow_redirects=False,headers={'User-Agent':'PULSE-Embedding/1.1'})
        if 300 <= r.status_code < 400: raise HTTPException(400,'Redirects are not permitted for input URLs')
        r.raise_for_status(); chunks=[]; total=0
        for chunk in r.iter_content(65536):
            total+=len(chunk)
            if total>MAX_BYTES: raise HTTPException(413,'Input exceeds size limit')
            chunks.append(chunk)
        return b''.join(chunks)
    except HTTPException: raise
    except requests.RequestException: raise HTTPException(400,'Unable to fetch input')

def fixed_dimension(values):
    v=np.asarray(values,dtype=np.float32).reshape(-1)
    if len(v)>DIM: v=v[:DIM]
    if len(v)<DIM: v=np.pad(v,(0,DIM-len(v)))
    n=float(np.linalg.norm(v))
    return (v/n if n else v).tolist()

def deterministic_text(text):
    raw=hashlib.sha512(text.encode()).digest(); out=[]
    for i in range(DIM): out.append(((raw[i%len(raw)]/255.0)*2.0)-1.0)
    return fixed_dimension(out)

@app.get('/health')
def health(): return {'status':'ok','dimension':DIM}

@app.post('/generate')
def generate(req:EmbeddingRequest,_service:bool=Depends(require_service)):
    if req.type=='text':
        if not req.text or not req.text.strip(): raise HTTPException(400,'Text required')
        return {'embedding':deterministic_text(req.text)}
    data=download(req.file_url)
    if req.type in ('image','face'):
        try:
            image=Image.open(io.BytesIO(data)).convert('RGB')
            small=np.asarray(image.resize((32,32)),dtype=np.float32).flatten()/255.0
            return {'embedding':fixed_dimension(small),'model':'visual-fingerprint-v1','note':'Configure a validated face/image model before biometric production use.'}
        except Exception: raise HTTPException(400,'Invalid image')
    if req.type=='voice':
        return {'embedding':fixed_dimension(np.frombuffer(data,dtype=np.uint8).astype(np.float32)),'model':'audio-fingerprint-v1','note':'Configure a validated speaker model before biometric production use.'}
    raise HTTPException(400,'Unsupported type')
