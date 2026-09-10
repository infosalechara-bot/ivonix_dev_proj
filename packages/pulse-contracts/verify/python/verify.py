#!/usr/bin/env python3
import json, re, sys
from pathlib import Path
import jsonschema

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
SCHEMAS = ROOT / "schemas"
FIXTURES = ROOT / "fixtures" / "v1"
PAYLOADS = FIXTURES / "payloads"
CASES = [("pulse_event_v1","pulse_event_v1.golden.json"),("pulse_error_v1","pulse_error_v1.golden.json"),("pulse_pagination_v1","pulse_pagination_v1.golden.json"),("pulse_claims_v1","pulse_claims_v1.golden.json")]
failures = 0

for schema_name, fixture_name in CASES:
    schema = json.loads((SCHEMAS / f"{schema_name}.json").read_text())
    fixture = json.loads((FIXTURES / fixture_name).read_text())
    try:
        jsonschema.Draft202012Validator(schema).validate(fixture)
        print(f"OK {fixture_name} -> {schema_name}")
    except jsonschema.ValidationError as exc:
        failures += 1
        print(f"FAIL {fixture_name} -> {schema_name}: {exc.message}")

envelope_schema = json.loads((SCHEMAS / "pulse_event_v1.json").read_text())
if PAYLOADS.exists():
    for fixture in sorted(PAYLOADS.glob("*.v1.golden.json")):
        event_type = fixture.name.replace(".v1.golden.json", "")
        payload = json.loads(fixture.read_text())
        envelope = {"event_id":"01HZ8X2K5M6P7Q8R9S0T1V2W3X","event_type":event_type,"event_version":1,"envelope_version":1,"occurred_at":"2025-01-15T08:30:00.000Z","producer":"pulse-core","aggregate_type":"unknown","aggregate_id":"01HZ8X2K5M6P7Q8R9S0T1V2W4Y","org_id":"01HZ8X2K5M6P7Q8R9S0T1V2W5Z","correlation_id":"01HZ8X2K5M6P7Q8R9S0T1V2W60","causation_id":None,"payload":payload,"signature":"MEUCIQDf7f8L7dFakedForGoldenFixtureTestingOnlyDoNotUseInProduction"}
        try:
            jsonschema.Draft202012Validator(envelope_schema).validate(envelope)
            print(f"OK payload {fixture.name} -> envelope")
        except jsonschema.ValidationError as exc:
            failures += 1
            print(f"FAIL payload {fixture.name} -> envelope: {exc.message}")

event_fixture = json.loads((FIXTURES / "pulse_event_v1.golden.json").read_text())
if re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z", event_fixture["occurred_at"]):
    print("OK occurred_at millisecond precision")
else:
    failures += 1
    print("FAIL occurred_at millisecond precision")

if failures:
    print(f"{failures} failure(s)")
    sys.exit(1)
print("Block 1 Python verification passed")
