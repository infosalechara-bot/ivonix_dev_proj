import json,sys
from pathlib import Path
import jsonschema
ROOT=Path(__file__).resolve().parents[2]
CASES=[('event-bus/envelope/v1/envelope.schema.json','pulse_event_v1.golden.json'),('api/error-envelope/v1/error-envelope.schema.json','pulse_error_v1.golden.json'),('api/pagination/v1/pagination.schema.json','pulse_pagination_v1.golden.json'),('api/request-id/v1/request-id.schema.json','request_id_v1.golden.json'),('identity/auth-claims/v1/auth-claims.schema.json','pulse_claims_v1.golden.json')]
for schema_rel,fixture in CASES:
 schema=json.loads((ROOT/schema_rel).read_text()); data=json.loads((ROOT/'fixtures/v1'/fixture).read_text()); jsonschema.Draft202012Validator(schema).validate(data); print(f'OK {fixture}')
print('All Block 1 canonical fixtures validate in Python.')
