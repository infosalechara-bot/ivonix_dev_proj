#!/usr/bin/env python3
"""Fail closed if active registry versions and golden payload fixtures drift."""
import json, os, sys, urllib.request
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
fixtures_dir = ROOT / 'packages/pulse-contracts/fixtures/v1/payloads'
url = os.getenv('SUPABASE_URL')
key = os.getenv('SUPABASE_SERVICE_ROLE_KEY')
if not url or not key:
    print('Registry coverage requires staging Supabase credentials; refusing to claim pass.')
    sys.exit(2)
if not fixtures_dir.exists():
    print(f'Missing fixtures directory: {fixtures_dir}')
    sys.exit(1)

fixture_map = {}
for fixture in sorted(fixtures_dir.glob('*.v1.golden.json')):
    name = fixture.name[:-len('.v1.golden.json')]
    key_tuple = (name, 1)
    if key_tuple in fixture_map:
        print(f'Duplicate fixture for {name}.v1')
        sys.exit(1)
    fixture_map[key_tuple] = fixture

request = urllib.request.Request(
    url.rstrip('/') + '/rest/v1/event_types?select=name,event_version,schema_id,schema_status,payload_schema',
    headers={'apikey': key, 'Authorization': 'Bearer ' + key},
)
with urllib.request.urlopen(request, timeout=30) as response:
    rows = json.load(response)

registered = {
    (row['name'], int(row['event_version'])): row
    for row in rows if row.get('schema_status') == 'active'
}
missing_fixtures = sorted(set(registered) - set(fixture_map))
missing_registry = sorted(set(fixture_map) - set(registered))
failures = False

for name, version in missing_fixtures:
    failures = True
    print(f'Missing fixture: {name}.v{version}')
for name, version in missing_registry:
    failures = True
    print(f'Fixture without active registry row: {name}.v{version}')

for key_tuple, row in sorted(registered.items()):
    name, version = key_tuple
    fixture = fixture_map.get(key_tuple)
    if fixture is None:
        continue
    schema_id = row.get('schema_id')
    if schema_id != f'pulse.event.{name}.v{version}':
        failures = True
        print(f'Invalid schema_id for {name}.v{version}: {schema_id!r}')
    schema = row.get('payload_schema')
    if not isinstance(schema, dict):
        failures = True
        print(f'Missing/invalid payload_schema for {name}.v{version}')
        continue
    try:
        data = json.loads(fixture.read_text(encoding='utf-8'))
        errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda e: list(e.path))
    except Exception as exc:
        failures = True
        print(f'Failed to validate {fixture.name}: {exc}')
        continue
    if errors:
        failures = True
        print(f'Payload fixture failed registry schema: {fixture.name}')
        for error in errors:
            print(f'  {error.message}')
    else:
        print(f'OK {fixture.name} -> {name}.v{version}')

if failures:
    sys.exit(1)
print(f'OK registry coverage: {len(registered)} active event versions with matching fixtures and valid payload schemas')
