#!/usr/bin/env python3
import json,os,sys,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; fixtures=ROOT/'packages/pulse-contracts/fixtures/v1/payloads'
url=os.getenv('SUPABASE_URL');key=os.getenv('SUPABASE_SERVICE_ROLE_KEY')
if not url or not key: print('Registry coverage requires test Supabase credentials; refusing to claim pass.');sys.exit(2)
fixture_keys={f.name.replace('.v1.golden.json','').replace('_','.')+'.1' for f in fixtures.glob('*.v1.golden.json')}
req=urllib.request.Request(url.rstrip('/')+'/rest/v1/event_types?select=name,event_version,schema_status',headers={'apikey':key,'Authorization':'Bearer '+key})
with urllib.request.urlopen(req) as r:rows=json.load(r)
active={f"{r['name']}.{r['event_version']}" for r in rows if r.get('schema_status')=='active'}
if active-fixture_keys or fixture_keys-active:
 print('Registry/fixture mismatch');print('Missing fixtures:',sorted(active-fixture_keys));print('Missing registry:',sorted(fixture_keys-active));sys.exit(1)
print(f'OK registry coverage: {len(active)} active event versions')
