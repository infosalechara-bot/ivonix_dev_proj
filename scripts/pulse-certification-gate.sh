#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PULSE_BASE_URL:-http://localhost:8081}"
K6_API="${PULSE_K6_API_URL:-${BASE_URL}}"
K6_DEVICE="${PULSE_K6_DEVICE_URL:-${BASE_URL}}"
RESULTS_DIR="${PULSE_RESULTS_DIR:-artifacts/pulse-certification}"
mkdir -p "$RESULTS_DIR"

fail=0
run_gate() {
  local name="$1"; shift
  echo "==> $name"
  if "$@"; then echo "PASS $name" | tee "$RESULTS_DIR/$name.status"; else echo "FAIL $name" | tee "$RESULTS_DIR/$name.status"; fail=1; fi
}

# These are deliberately separate gates so a passing build cannot hide a failing runtime layer.
run_gate smoke bash -c 'curl --fail --silent --show-error "$0/actuator/health" >/dev/null' "$BASE_URL"

if command -v k6 >/dev/null 2>&1; then
  if [[ "${PULSE_RUN_LOAD:-0}" == "1" ]]; then
    run_gate machine-load k6 run --summary-export "$RESULTS_DIR/machine-load.json" k6/machine-ingestion.js
    run_gate api-read-load k6 run --summary-export "$RESULTS_DIR/api-read-load.json" k6/api-read-load.js
  else
    echo "SKIP load: set PULSE_RUN_LOAD=1" | tee "$RESULTS_DIR/load.status"
  fi
else
  echo "SKIP load: k6 not installed" | tee "$RESULTS_DIR/load.status"
fi

# A deployment is not certified unless explicit environment evidence is supplied.
for required in PULSE_EXPECTED_SCHEMA_VERSION PULSE_DEPLOYMENT_ID; do
  if [[ -z "${!required:-}" ]]; then
    echo "MISSING certification evidence: $required" >&2
    fail=1
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "PULSE certification gate: FAILED"
  exit 1
fi

echo "PULSE certification gate: PASS (all executed gates passed)"
