#!/usr/bin/env bash
# RozPost production smoke: onboard → WA media+instructions → enhance → approve → mock publish
set -euo pipefail

API="${API_BASE_URL:-http://localhost:8080}"
PHONE="${SMOKE_PHONE:-+919876543210}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Health / webhook verify"
curl -sf "$API/actuator/health" >/dev/null || curl -sf "$API/api/v1/health" >/dev/null
curl -sf "$API/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=${WEBHOOK_VERIFY_TOKEN:-demo-verify}&hub.challenge=ok" | grep -q ok

echo "==> 1) Onboard"
ONBOARD=$(curl -sS -X POST "$API/api/v1/onboarding" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: smoke-$(date +%s)" \
  -d "{\"businessName\":\"Smoke Salon\",\"phoneE164\":\"$PHONE\",\"city\":\"Ludhiana\",\"category\":\"salon\"}")
echo "$ONBOARD" | tee /tmp/rozpost-onboard.json
WA_USER=$(python3 -c 'import json; print(json.load(open("/tmp/rozpost-onboard.json"))["waUserId"])')

echo "==> 2) Simulate WA media + instructions (META_MODE=mock)"
MSG=$(curl -sS -X POST "$API/api/v1/simulations/wa-message" \
  -H 'Content-Type: application/json' \
  -d "{\"waUserId\":\"$WA_USER\",\"instructionText\":\"Make background soft beige, keep face natural\"}")
echo "$MSG" | tee /tmp/rozpost-msg.json
POST_ID=$(python3 -c 'import json; print(json.load(open("/tmp/rozpost-msg.json"))["postId"])')
STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/rozpost-msg.json"))["status"])')
test "$STATUS" = "awaiting_approval"

if [[ "${SMOKE_DISAPPROVE_ONCE:-0}" == "1" ]]; then
  echo "==> 3a) Disapprove once with feedback"
  curl -sS -X POST "$API/api/v1/approvals/wa-button" \
    -H 'Content-Type: application/json' \
    -d "{\"waUserId\":\"$WA_USER\",\"postId\":\"$POST_ID\",\"button\":\"disapprove\",\"feedbackText\":\"Warmer tones please\"}" \
    | tee /tmp/rozpost-disapprove.json
fi

echo "==> 3) Approve → ig-publish"
APPROVE=$(curl -sS -X POST "$API/api/v1/approvals/wa-button" \
  -H 'Content-Type: application/json' \
  -d "{\"waUserId\":\"$WA_USER\",\"postId\":\"$POST_ID\",\"button\":\"approve\"}")
echo "$APPROVE" | tee /tmp/rozpost-approve.json

echo "==> 4) Wait for Published + permalink"
for i in $(seq 1 45); do
  POST=$(curl -sS "$API/api/v1/posts/$POST_ID")
  ST=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['status'])" "$POST")
  PERM=$(python3 -c "import json,sys; print(json.loads(sys.argv[1]).get('igPermalink') or '')" "$POST")
  echo "  poll $i: status=$ST permalink=$PERM"
  if [[ "$ST" == "published" && -n "$PERM" ]]; then
    echo "$POST" | tee /tmp/rozpost-published.json
    echo "SMOKE OK postId=$POST_ID permalink=$PERM"
    exit 0
  fi
  sleep 1
done

echo "SMOKE FAILED: post did not reach published" >&2
curl -sS "$API/api/v1/posts/$POST_ID" >&2 || true
exit 1
