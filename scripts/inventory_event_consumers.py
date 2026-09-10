#!/usr/bin/env python3
import json,re
from pathlib import Path
ROOT=Path('.');exclude={'.git','node_modules','target','dist','build','venv','.venv'};patterns={'event_type_literal':r'event_type[s]?\s*[:=]\s*[\'\"]([^\'\"]+)[\'\"]','envelope_use':r'PulseEvent|pulse_event_v1','error_contract':r'PulseError|pulse_error_v1|error_legacy','pagination_contract':r'PulsePage|pulse_pagination_v1','claims_contract':r'PulseClaims|pulse_claims_v1|actor_type'};out={k:[] for k in patterns}
for f in ROOT.rglob('*'):
 if not f.is_file() or any(x in exclude for x in f.parts) or f.suffix not in {'.java','.py','.ts','.tsx','.js','.jsx'}:continue
 try:t=f.read_text(encoding='utf8',errors='ignore')
 except OSError:continue
 for k,p in patterns.items():
  for m in re.finditer(p,t):out[k].append({'file':str(f),'line':t.count('\n',0,m.start())+1,'match':m.group(0)[:160]})
Path('block1_consumers.json').write_text(json.dumps(out,indent=2));print('Wrote block1_consumers.json')
