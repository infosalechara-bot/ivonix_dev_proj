from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
import base64, hashlib, hmac, io, os, re, requests, qrcode
app=FastAPI()
SECRET=os.environ.get('PULSE_CERT_RENDERER_SECRET','')
SUPA=os.environ['SUPABASE_URL'].rstrip('/')
KEY=os.environ['SUPABASE_SERVICE_ROLE_KEY']
class CertRequest(BaseModel):
 certificateId:str; courseTitle:str; userName:str; certificateNumber:str; signature:str; issuedAt:str; storagePath:str
def auth(v):
 if not SECRET or not v or not v.lower().startswith('bearer '): raise HTTPException(401,'Unauthorized')
 if not hmac.compare_digest(v[7:],SECRET): raise HTTPException(401,'Unauthorized')
def safe_path(p):
 if not re.fullmatch(r'certificates/[0-9a-f-]{36}/[0-9a-f-]{36}\.pdf',p): raise HTTPException(400,'Invalid storage path')
 return p
@app.get('/health')
def health(): return {'status':'ok'}
@app.post('/render')
def render(req:CertRequest,authorization:str=Header(default='')):
 auth(authorization); path=safe_path(req.storagePath); out=io.BytesIO(); c=canvas.Canvas(out,pagesize=A4); w,h=A4
 c.setTitle('PULSE Academy Certificate'); c.setFont('Helvetica-Bold',28); c.drawCentredString(w/2,h-5*72,'PULSE Academy'); c.setFont('Helvetica',14); c.drawCentredString(w/2,h-6*72,'Certificate of Completion'); c.setFont('Helvetica-Bold',22); c.drawCentredString(w/2,h-10*72,req.userName[:120]); c.setFont('Helvetica',12); c.drawCentredString(w/2,h-12*72,'has successfully completed'); c.setFont('Helvetica-Bold',16); c.drawCentredString(w/2,h-14*72,req.courseTitle[:140]); c.setFont('Helvetica',9); c.drawCentredString(w/2,h-18*72,'Certificate No: '+req.certificateNumber); c.drawCentredString(w/2,h-19*72,'Issued: '+req.issuedAt)
 qr=qrcode.make(f'/api/academy/certificates/verify/{req.certificateNumber}'); b=io.BytesIO(); qr.save(b,format='PNG'); b.seek(0); c.drawImage(ImageReader(b),w-5*72,3*72,3*72,3*72); c.showPage(); c.save(); data=out.getvalue()
 r=requests.post(f'{SUPA}/storage/v1/object/pulse-assets/{path}',headers={'Authorization':f'Bearer {KEY}','apikey':KEY,'Content-Type':'application/pdf','x-upsert':'false'},data=data,timeout=30); r.raise_for_status(); return {'size':len(data),'path':path,'sha256':hashlib.sha256(data).hexdigest()}
