#!/usr/bin/env bash
set -euo pipefail
API="${API_BASE_URL:-http://localhost:8080}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIX="$ROOT/packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json"
CORR="smoke-$(date +%s)"

echo "== health =="
curl -sS -H "X-Correlation-Id: $CORR" "$API/api/v1/health"
echo
echo "== wa ingest fixture =="
curl -sS -X POST "$API/webhooks/whatsapp" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORR" \
  --data-binary @"$FIX"
echo
