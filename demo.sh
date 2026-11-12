#!/usr/bin/env bash
# Walks through the main flow against a running instance (default http://localhost:8080).
set -euo pipefail
BASE=${BASE:-http://localhost:8080}/api/catalogs/demo
j() { if command -v jq >/dev/null; then jq .; else cat; echo; fi; }

echo "== 1. Full load: 12 products, some with bad data"
curl -s -X PUT "$BASE/products" -H 'Content-Type: application/json' --data @sample-data/sample-catalog-full.json | j

echo "== 2. Data Health summary"
curl -s "$BASE/health" | j

echo "== 3. Errors to fix"
curl -s "$BASE/health/issues?severity=ERROR" | j

echo "== 4. Partial update: fix GL-800 and BP-400, plus one stale TR-100 update"
curl -s -X PATCH "$BASE/products" -H 'Content-Type: application/json' --data @sample-data/sample-catalog-partial.json | j

echo "== 5. Health after the fix"
curl -s "$BASE/health" | j
