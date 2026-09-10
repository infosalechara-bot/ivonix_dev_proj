#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Block 1 - Contract Verification"

echo "[Python]"
python -m pip install -q -r packages/pulse-contracts/verify/python/requirements.txt
python packages/pulse-contracts/verify/python/verify.py

echo "[TypeScript]"
cd packages/pulse-contracts/verify/typescript
npm install --no-audit --no-fund --silent
npm run --silent verify

cd "$ROOT/packages/pulse-contracts/verify/java"
echo "[Java]"
mvn -q test

cd "$ROOT"
echo "[Registry coverage]"
python scripts/check_registry_coverage.py

echo "Block 1 verification PASSED"
