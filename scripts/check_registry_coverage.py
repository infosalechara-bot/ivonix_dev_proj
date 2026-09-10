#!/usr/bin/env python3
"""Verify bidirectional event registry/fixture coverage and payload-schema validity."""
import json
import os
import sys
import urllib.request
from pathlib import Path

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
fixtures_dir = ROOT / "packages/pulse-contracts/fixtures/v1/payloads"
url = os.getenv("SUPABASE_URL")
key = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

if not url or not key:
    print("Registry coverage requires test Supabase credentials; refusing to claim pass.")
    sys.exit(2)
if not fixtures_dir.exists():
    print(f"Missing fixtures directory: {fixtures_dir}")
    sys.exit(1)

# Fixture names intentionally preserve the registry's canonical event name verbatim.
# Example: device_telemetry_received.v1.golden.json -> (device_telemetry_received, 1).
fixture_map = {}
for fixture in sorted(fixtures_dir.glob("*.v1.golden.json")):
    event_name = fixture.name[: -len(".v1.golden.json")]
    key_tuple = (event_name, 1)
    if key_tuple in fixture_map:
        print(f"Duplicate fixture for {event_name}.v1")
        sys.exit(1)
    fixture_map[key_tuple] = fixture

request = urllib.request.Request(
    url.rstrip("/") + "/rest/v1/event_types?select=name,event_version,schema_status,payload_schema",
    headers={"apikey": key, "Authorization": "Bearer " + key},
)
with urllib.request.urlopen(request, timeout=30) as response:
    rows = json.load(response)

# Retired schemas are no longer valid emission targets and therefore are excluded.
registered = {
    (row["name"], int(row["event_version"])): row
    for row in rows
    if row.get("schema_status") in {"active", "deprecated"}
}

missing_fixtures = sorted(set(registered) - set(fixture_map))
missing_registry = sorted(set(fixture_map) - set(registered))

failures = False
if missing_fixtures:
    failures = True
    print("Missing fixtures for registered event versions:")
    for name, version in missing_fixtures:
        print(f"  {name}.v{version}")
if missing_registry:
    failures = True
    print("Fixtures without registered event versions:")
    for name, version in missing_registry:
        print(f"  {name}.v{version}")

# Every registered event must have a payload schema and its canonical fixture must validate it.
for key_tuple, row in sorted(registered.items()):
    fixture = fixture_map.get(key_tuple)
    if fixture is None:
        continue
    schema = row.get("payload_schema")
    if not isinstance(schema, dict):
        failures = True
        print(f"Missing/invalid payload_schema for {row['name']}.v{row['event_version']}")
        continue
    try:
        data = json.loads(fixture.read_text(encoding="utf-8"))
        errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda e: list(e.path))
    except Exception as exc:
        failures = True
        print(f"Failed to load/validate {fixture.name}: {exc}")
        continue
    if errors:
        failures = True
        print(f"Payload fixture failed registry schema: {fixture.name}")
        for error in errors:
            print(f"  {error.message}")
    else:
        print(f"OK {fixture.name} -> {row['name']}.v{row['event_version']}")

if failures:
    sys.exit(1)

print(f"OK registry coverage: {len(registered)} registered event versions with matching fixtures and valid payloads")
