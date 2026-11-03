#!/usr/bin/env bash
set -euo pipefail
API="${API_BASE_URL:-http://localhost:8080}"
FIX="$(cd "$(dirname "$0")/.." && pwd)/packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json"
curl -sS -X POST "$API/webhooks/whatsapp" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: smoke-wa-ingest" \
  --data-binary @"$FIX"
echo
