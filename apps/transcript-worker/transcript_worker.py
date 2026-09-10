import os
import tempfile
import time
import uuid

import requests
from faster_whisper import WhisperModel

SUPA = os.environ['SUPABASE_URL'].rstrip('/')
KEY = os.environ['SUPABASE_SERVICE_ROLE_KEY']
H = {
    'apikey': KEY,
    'Authorization': f'Bearer {KEY}',
    'Content-Type': 'application/json',
    'Prefer': 'return=representation',
}
MODEL = os.getenv('WHISPER_MODEL', 'base')
DEVICE = os.getenv('WHISPER_DEVICE', 'auto')
COMPUTE = os.getenv('WHISPER_COMPUTE_TYPE', 'int8')
WORKER_ID = os.getenv('WORKER_ID', f'transcript-{uuid.uuid4()}')
LEASE_SECONDS = max(30, min(int(os.getenv('LEASE_SECONDS', '300')), 3600))
STORAGE_BUCKET = os.getenv('TRANSCRIPT_STORAGE_BUCKET', 'pulse-assets')
MAX_RECORDING_BYTES = max(1, min(int(os.getenv('MAX_RECORDING_BYTES', str(50 * 1024 * 1024))), 50 * 1024 * 1024))

model = WhisperModel(MODEL, device=DEVICE, compute_type=COMPUTE)


def claim():
    response = requests.post(
        f'{SUPA}/rest/v1/rpc/claim_meet_recording',
        headers=H,
        json={'p_worker_id': WORKER_ID, 'p_lease_seconds': LEASE_SECONDS},
        timeout=15,
    )
    response.raise_for_status()
    return response.json()


def download(rec):
    path = rec.get('storage_path')
    organization_id = str(rec.get('organization_id') or '')
    if not path or not organization_id:
        raise ValueError('recording storage path or organization id missing')
    if path.startswith('/') or '..' in path.split('/'):
        raise ValueError('invalid recording storage path')
    if not path.startswith(f'{organization_id}/'):
        raise ValueError('recording storage path is outside its tenant prefix')

    fd, temp_path = tempfile.mkstemp(suffix='.webm')
    os.close(fd)
    try:
        with requests.get(
            f'{SUPA}/storage/v1/object/{STORAGE_BUCKET}/{path}',
            headers=H,
            stream=True,
            timeout=30,
        ) as response:
            response.raise_for_status()
            total = 0
            with open(temp_path, 'wb') as output:
                for chunk in response.iter_content(1024 * 1024):
                    if not chunk:
                        continue
                    total += len(chunk)
                    if total > MAX_RECORDING_BYTES:
                        raise ValueError('recording exceeds configured storage size limit')
                    output.write(chunk)
        return temp_path
    except Exception:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise


def transcribe(path, language=None):
    segments, info = model.transcribe(path, language=language, vad_filter=True, beam_size=5)
    output = []
    for segment in segments:
        output.append({'start': float(segment.start), 'end': float(segment.end), 'text': segment.text.strip()})
    return output, info.language


def finish(rec, segments, lang):
    response = requests.post(
        f'{SUPA}/rest/v1/meet_transcripts',
        headers=H,
        json={'recording_id': rec['id'], 'language': lang, 'transcript_json': segments},
        timeout=30,
    )
    response.raise_for_status()
    response = requests.patch(
        f'{SUPA}/rest/v1/meet_recordings',
        headers=H,
        params={'id': f"eq.{rec['id']}", 'transcription_worker_id': f'eq.{WORKER_ID}'},
        json={'status': 'completed', 'transcription_lease_until': None, 'transcription_worker_id': None},
        timeout=15,
    )
    response.raise_for_status()


def fail(rec, msg):
    response = requests.patch(
        f'{SUPA}/rest/v1/meet_recordings',
        headers=H,
        params={'id': f"eq.{rec['id']}", 'transcription_worker_id': f'eq.{WORKER_ID}'},
        json={'status': 'failed', 'transcription_lease_until': None, 'transcription_worker_id': None},
        timeout=15,
    )
    response.raise_for_status()


def main():
    while True:
        recs = claim()
        for rec in recs:
            temp_path = None
            try:
                temp_path = download(rec)
                segments, language = transcribe(temp_path)
                finish(rec, segments, language)
            except Exception as exc:
                try:
                    fail(rec, str(exc)[:500])
                except requests.RequestException:
                    pass
            finally:
                if temp_path:
                    try:
                        os.unlink(temp_path)
                    except OSError:
                        pass
        time.sleep(int(os.getenv('POLL_SECONDS', '10')))


if __name__ == '__main__':
    main()
