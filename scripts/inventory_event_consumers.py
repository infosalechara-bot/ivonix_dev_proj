#!/usr/bin/env python3
"""Read-only inventory of event publishers, subscribers, and contract references."""
import json
import re
from pathlib import Path

ROOT = Path('.')
EXCLUDE = {'.git','node_modules','target','dist','build','venv','.venv','__pycache__'}
PATTERNS = {
    'mqtt_subscribe': re.compile(r'\.subscribe\(\s*[\'\"]([^\'\"]+)[\'\"]'),
    'mqtt_publish': re.compile(r'\.publish\(\s*[\'\"]([^\'\"]+)[\'\"]'),
    'event_type_literal': re.compile(r'event[_-]?type[s]?\s*[:=]\s*[\'\"]([a-z0-9_.]+)[\'\"]'),
    'envelope_use': re.compile(r'PulseEvent|pulse_event_v1'),
    'error_contract': re.compile(r'PulseError|pulse_error_v1|error_legacy'),
    'pagination_contract': re.compile(r'PulsePage|pulse_pagination_v1|cursor'),
    'claims_contract': re.compile(r'PulseClaims|pulse_claims_v1|actor_type'),
}
OUT = {key: [] for key in PATTERNS}

for path in ROOT.rglob('*'):
    if not path.is_file() or any(part in EXCLUDE for part in path.parts):
        continue
    if path.suffix not in {'.java','.py','.ts','.tsx','.js','.jsx'}:
        continue
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except OSError:
        continue
    for key, pattern in PATTERNS.items():
        for match in pattern.finditer(text):
            OUT[key].append({
                'file': str(path),
                'line': text.count('\n', 0, match.start()) + 1,
                'match': match.group(0)[:160],
            })

Path('block1_consumers.json').write_text(json.dumps(OUT, indent=2), encoding='utf-8')
print('Block 1 - consumer inventory')
for key, values in OUT.items():
    print(f'  {key:20} {len(values):>5}')
print('Full report: block1_consumers.json')
