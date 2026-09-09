import os,base64,hashlib
from fastapi import FastAPI,HTTPException
from pydantic import BaseModel
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import requests

app=FastAPI(title='PULSE KEY Crypto Service')
URL=os.environ.get('SUPABASE_URL','').rstrip('/')
SERVICE_KEY=os.environ.get('SUPABASE_SERVICE_ROLE_KEY','')
MASTER_RAW=os.environ.get('PULSE_MASTER_KEY','')
if not URL or not SERVICE_KEY or not MASTER_RAW: raise RuntimeError('SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and PULSE_MASTER_KEY are required')
try: MASTER=base64.b64decode(MASTER_RAW)
except Exception as e: raise RuntimeError('PULSE_MASTER_KEY must be base64') from e
if len(MASTER)!=32: raise RuntimeError('PULSE_MASTER_KEY must decode to 32 bytes')
H={'apikey':SERVICE_KEY,'Authorization':f'Bearer {SERVICE_KEY}','Content-Type':'application/json'}
class Req(BaseModel): keyId:str; data:str

def row(key_id):
 r=requests.get(f'{URL}/rest/v1/crypto_keys',params={'id':f'eq.{key_id}','status':'eq.active','select':'id,key_type,encrypted_private_key'},headers=H,timeout=5);r.raise_for_status();a=r.json();
 if len(a)!=1: raise HTTPException(404,'Key unavailable')
 return a[0]
def unwrap(value):
 raw=base64.b64decode(value);iv,ct=raw[:12],raw[12:];return AESGCM(MASTER).decrypt(iv,ct,b'pulse-key-v1')
def key_for(rec):
 if rec['key_type']!='aes-256': raise HTTPException(400,'This endpoint currently supports AES-256 encryption keys only')
 try:k=unwrap(rec['encrypted_private_key'])
 except Exception:raise HTTPException(500,'Key unwrap failed')
 if len(k)!=32:raise HTTPException(500,'Invalid AES-256 key material')
 return k

def audit(key_id,op,status):
 try: requests.post(f'{URL}/rest/v1/crypto_operations',headers={**H,'Prefer':'return=minimal'},json={'key_id':key_id,'operation_type':op,'status':status},timeout=5).raise_for_status()
 except Exception: pass
@app.get('/health')
def health(): return {'status':'ok','hsm_backed':False,'mode':'encrypted-key-storage'}
@app.post('/v1/encrypt')
def encrypt(req:Req):
 try:
  k=key_for(row(req.keyId));iv=os.urandom(12);ct=AESGCM(k).encrypt(iv,req.data.encode(),b'pulse-key-v1');audit(req.keyId,'encrypt','success');return {'ciphertext':base64.b64encode(iv+ct).decode()}
 except HTTPException: audit(req.keyId,'encrypt','failed');raise
 except Exception as e: audit(req.keyId,'encrypt','failed');raise HTTPException(500,'Encryption failed') from e
@app.post('/v1/decrypt')
def decrypt(req:Req):
 try:
  k=key_for(row(req.keyId));raw=base64.b64decode(req.data);pt=AESGCM(k).decrypt(raw[:12],raw[12:],b'pulse-key-v1');audit(req.keyId,'decrypt','success');return {'plaintext':pt.decode()}
 except HTTPException: audit(req.keyId,'decrypt','failed');raise
 except Exception as e: audit(req.keyId,'decrypt','failed');raise HTTPException(400,'Decryption failed') from e
