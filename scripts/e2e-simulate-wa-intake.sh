#!/usr/bin/env bash
set -euo pipefail
# Production-grade local smoke (requires META_MODE=mock).
API="${API_BASE_URL:-http://localhost:8080}"
CLIENT_ID="${1:-22222222-2222-2222-2222-222222222222}"
INSTRUCTION="${2:-Soft beige background, keep face natural}"
curl -sS -X POST "$API/api/v1/internal/simulate-wa-message" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: e2e-$(date +%s)" \
  -d "{\"clientId\":\"$CLIENT_ID\",\"instructionText\":\"$INSTRUCTION\"}"
echo
