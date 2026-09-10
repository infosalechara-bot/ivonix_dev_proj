import json
from pathlib import Path
import jsonschema
ROOT=Path(__file__).resolve().parents[2]
def test_all_canonical_fixtures():
 cases=[('event-bus/envelope/v1/envelope.schema.json','pulse_event_v1.golden.json'),('api/error-envelope/v1/error-envelope.schema.json','pulse_error_v1.golden.json'),('api/pagination/v1/pagination.schema.json','pulse_pagination_v1.golden.json'),('api/request-id/v1/request-id.schema.json','request_id_v1.golden.json'),('identity/auth-claims/v1/auth-claims.schema.json','pulse_claims_v1.golden.json')]
 for schema,fixture in cases:jsonschema.Draft202012Validator(json.loads((ROOT/schema).read_text())).validate(json.loads((ROOT/'fixtures/v1'/fixture).read_text()))
