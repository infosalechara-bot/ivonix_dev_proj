import Ajv from "ajv";
import addFormats from "ajv-formats";
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "../..");
const SCHEMAS = join(ROOT, "schemas");
const FIXTURES = join(ROOT, "fixtures/v1");
const PAYLOADS = join(FIXTURES, "payloads");

const ajv = new Ajv({ strict: false, allErrors: true });
addFormats(ajv);
const cases = [
  ["pulse_event_v1", "pulse_event_v1.golden.json"],
  ["pulse_error_v1", "pulse_error_v1.golden.json"],
  ["pulse_pagination_v1", "pulse_pagination_v1.golden.json"],
  ["pulse_claims_v1", "pulse_claims_v1.golden.json"],
] as const;
let failures = 0;

for (const [schemaName, fixtureName] of cases) {
  const validate = ajv.compile(JSON.parse(readFileSync(join(SCHEMAS, `${schemaName}.json`), "utf8")));
  const data = JSON.parse(readFileSync(join(FIXTURES, fixtureName), "utf8"));
  if (validate(data)) console.log(`OK ${fixtureName} -> ${schemaName}`);
  else { failures++; console.error(`FAIL ${fixtureName} -> ${schemaName}`, validate.errors); }
}

const envelope = ajv.compile(JSON.parse(readFileSync(join(SCHEMAS, "pulse_event_v1.json"), "utf8")));
const timestamp = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;
if (existsSync(PAYLOADS)) {
  for (const file of readdirSync(PAYLOADS).filter(x => x.endsWith(".v1.golden.json")).sort()) {
    const eventType = file.replace(".v1.golden.json", "");
    const payload = JSON.parse(readFileSync(join(PAYLOADS, file), "utf8"));
    const value = { event_id:"01HZ8X2K5M6P7Q8R9S0T1V2W3X", event_type:eventType, event_version:1, envelope_version:1, occurred_at:"2025-01-15T08:30:00.000Z", producer:"pulse-core", aggregate_type:"unknown", aggregate_id:"01HZ8X2K5M6P7Q8R9S0T1V2W4Y", org_id:"01HZ8X2K5M6P7Q8R9S0T1V2W5Z", correlation_id:"01HZ8X2K5M6P7Q8R9S0T1V2W60", causation_id:null, payload, signature:"MEUCIQDf7f8L7dFakedForGoldenFixtureTestingOnlyDoNotUseInProduction" };
    if (envelope(value)) console.log(`OK payload ${file} -> envelope`);
    else { failures++; console.error(`FAIL payload ${file} -> envelope`, envelope.errors); }
  }
}
const eventFixture = JSON.parse(readFileSync(join(FIXTURES, "pulse_event_v1.golden.json"), "utf8"));
if (!timestamp.test(eventFixture.occurred_at)) { failures++; console.error("FAIL occurred_at timestamp precision"); }
else console.log("OK occurred_at millisecond precision");

if (failures) { console.error(`${failures} failure(s)`); process.exit(1); }
console.log("Block 1 TypeScript verification passed");
