import os,time,tempfile,requests
from faster_whisper import WhisperModel
SUPA=os.environ['SUPABASE_URL'].rstrip('/'); KEY=os.environ['SUPABASE_SERVICE_ROLE_KEY']; H={'apikey':KEY,'Authorization':f'Bearer {KEY}','Content-Type':'application/json','Prefer':'return=representation'}; MODEL=os.getenv('WHISPER_MODEL','base'); DEVICE=os.getenv('WHISPER_DEVICE','auto'); COMPUTE=os.getenv('WHISPER_COMPUTE_TYPE','int8')
model=WhisperModel(MODEL,device=DEVICE,compute_type=COMPUTE)
def claim():
 r=requests.patch(f'{SUPA}/rest/v1/meet_recordings',headers=H,params={'status':'eq.processing','transcription_started_at':'is.null'},json={'transcription_started_at':time.strftime('%Y-%m-%dT%H:%M:%SZ')},timeout=15);r.raise_for_status();return r.json()
def download(rec):
 p=tempfile.mktemp(suffix='.webm'); path=rec.get('storage_path');
 if not path: raise ValueError('recording storage path missing')
 with requests.get(f'{SUPA}/storage/v1/object/recordings/{path}',headers=H,stream=True,timeout=30) as r:
  r.raise_for_status(); total=0
  with open(p,'wb') as f:
   for chunk in r.iter_content(1024*1024):
    total+=len(chunk)
    if total>2*1024*1024*1024: raise ValueError('recording exceeds 2GB limit')
    f.write(chunk)
 return p
def transcribe(p,language=None):
 segments,info=model.transcribe(p,language=language,vad_filter=True,beam_size=5); out=[]
 for s in segments: out.append({'start':float(s.start),'end':float(s.end),'text':s.text.strip()})
 return out,info.language
def save(rec,segments,lang):
 r=requests.post(f'{SUPA}/rest/v1/meet_transcripts',headers=H,json={'recording_id':rec['id'],'language':lang,'transcript_json':segments},timeout=30);r.raise_for_status();requests.patch(f"{SUPA}/rest/v1/meet_recordings",headers=H,params={'id':f"eq.{rec['id']}"},json={'status':'completed'},timeout=15).raise_for_status()
def fail(rec,msg): requests.patch(f"{SUPA}/rest/v1/meet_recordings",headers=H,params={'id':f"eq.{rec['id']}"},json={'status':'failed'},timeout=15).raise_for_status()
def main():
 while True:
  for rec in claim():
   p=None
   try:p=download(rec);segments,lang=transcribe(p);save(rec,segments,lang)
   except Exception as e: fail(rec,str(e)[:500])
   finally:
    if p:
     try:os.unlink(p)
     except OSError:pass
  time.sleep(int(os.getenv('POLL_SECONDS','10')))
if __name__=='__main__':main()
