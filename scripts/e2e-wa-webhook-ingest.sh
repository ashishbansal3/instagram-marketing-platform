#!/usr/bin/env bash
set -euo pipefail
# META_MODE=mock WA webhook ingest using packages/meta-mocks fixture.
API="${API_BASE_URL:-http://localhost:8080}"
FIX="$(cd "$(dirname "$0")/.." && pwd)/packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json"
curl -sS -X POST "$API/webhooks/whatsapp" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: e2e-webhook-$(date +%s)" \
  --data-binary @"$FIX"
echo
