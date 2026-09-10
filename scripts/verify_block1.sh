#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.."&&pwd)";cd "$ROOT"
python -m pip install -q -r packages/pulse-contracts/verify/python/requirements.txt
python packages/pulse-contracts/verify/python/verify.py
cd packages/pulse-contracts/verify/typescript && npm install --no-audit --no-fund --silent && npx tsx verify.ts && npm test -- --run
cd "$ROOT/packages/pulse-contracts/verify/java" && mvn -q test
