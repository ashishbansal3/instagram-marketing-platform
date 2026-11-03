#!/usr/bin/env bash
# RozPost META_MODE=mock E2E (real pipeline):
#   onboard → WA mock webhook → media-download (WA stub) → enhance/Remotion → assets-ready
#   → approve → mock IG publish
# Requires MOCK_AUTO_ENHANCE=false (compose default). Does not use simulation short-circuit.
set -euo pipefail

API="${API_BASE_URL:-http://localhost:8080}"
VERIFY_TOKEN="${WEBHOOK_VERIFY_TOKEN:-local-verify}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURE="$ROOT/packages/meta-mocks/fixtures/whatsapp/inbound-image-with-instruction.json"
SUFFIX="$(date +%s)"
PHONE="${SMOKE_PHONE:-+9198${SUFFIX: -8}}"
WAMID="wamid.SMOKE_${SUFFIX}"

echo "==> Health + webhook verify"
for i in $(seq 1 60); do
  if curl -sf "$API/api/v1/health" >/dev/null 2>&1 || curl -sf "$API/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if [[ "$i" -eq 60 ]]; then
    echo "API not healthy at $API" >&2
    exit 1
  fi
  sleep 2
done
curl -sf "$API/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=${VERIFY_TOKEN}&hub.challenge=ok" | grep -q ok

echo "==> 1) Onboard phone=$PHONE"
ONBOARD=$(curl -sS -X POST "$API/api/v1/onboarding" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: smoke-onboard-$SUFFIX" \
  -d "{\"businessName\":\"Smoke Salon $SUFFIX\",\"phoneE164\":\"$PHONE\",\"city\":\"Ludhiana\",\"category\":\"salon\"}")
echo "$ONBOARD" | tee /tmp/rozpost-onboard.json
WA_USER=$(python3 -c 'import json; print(json.load(open("/tmp/rozpost-onboard.json"))["waUserId"])')

echo "==> 2) WA mock webhook (media + instruction) — exercises worker download stub"
BODY=$(python3 - <<PY
import json
from pathlib import Path
root = json.loads(Path("$FIXTURE").read_text())
msg = root["entry"][0]["changes"][0]["value"]["messages"][0]
msg["from"] = "$WA_USER"
msg["id"] = "$WAMID"
root["entry"][0]["changes"][0]["value"]["contacts"][0]["wa_id"] = "$WA_USER"
print(json.dumps(root))
PY
)
INGEST=$(curl -sS -X POST "$API/webhooks/whatsapp" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: smoke-wa-$SUFFIX" \
  -d "$BODY")
echo "$INGEST" | tee /tmp/rozpost-ingest.json
POST_ID=$(python3 -c 'import json; print(json.load(open("/tmp/rozpost-ingest.json"))["postId"])')
STATUS=$(python3 -c 'import json; print(json.load(open("/tmp/rozpost-ingest.json")).get("status",""))')
test "$STATUS" = "queued_media_download"
test -n "$POST_ID"

# Fail fast if API short-circuited to awaiting_approval (mock-auto-enhance must be off).
IMMEDIATE=$(curl -sS "$API/api/v1/posts/$POST_ID")
IMMEDIATE_ST=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['status'])" "$IMMEDIATE")
if [[ "$IMMEDIATE_ST" == "awaiting_approval" ]]; then
  echo "SMOKE FAILED: post already awaiting_approval right after webhook — mock-auto-enhance short-circuit?" >&2
  echo "$IMMEDIATE" >&2
  exit 1
fi
test "$IMMEDIATE_ST" = "media_processing"

echo "==> 3) Wait for media-worker + assets-ready → awaiting_approval (postId=$POST_ID)"
SAW_PROCESSING=0
for i in $(seq 1 90); do
  POST=$(curl -sS "$API/api/v1/posts/$POST_ID")
  ST=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['status'])" "$POST")
  echo "  poll media $i: status=$ST"
  if [[ "$ST" == "media_processing" ]]; then
    SAW_PROCESSING=1
  fi
  if [[ "$ST" == "awaiting_approval" ]]; then
    if [[ "$SAW_PROCESSING" != "1" && "$i" -eq 1 ]]; then
      echo "SMOKE FAILED: skipped media_processing (short-circuit)" >&2
      exit 1
    fi
    echo "$POST" | tee /tmp/rozpost-ready.json
    break
  fi
  if [[ "$ST" == "publish_failed" || "$ST" == "abandoned_needs_new_media" ]]; then
    echo "SMOKE FAILED during media: $POST" >&2
    exit 1
  fi
  if [[ "$i" -eq 90 ]]; then
    echo "SMOKE FAILED: media did not reach awaiting_approval (assets-ready / worker missing?)" >&2
    echo "$POST" >&2
    exit 1
  fi
  sleep 2
done

if [[ "${SMOKE_DISAPPROVE_ONCE:-0}" == "1" ]]; then
  echo "==> 3a) Disapprove once with feedback"
  curl -sS -X POST "$API/api/v1/approvals/wa-button" \
    -H 'Content-Type: application/json' \
    -d "{\"waUserId\":\"$WA_USER\",\"postId\":\"$POST_ID\",\"button\":\"disapprove\",\"feedbackText\":\"Warmer tones please\"}" \
    | tee /tmp/rozpost-disapprove.json
  for i in $(seq 1 60); do
    ST=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['status'])" "$(curl -sS "$API/api/v1/posts/$POST_ID")")
    echo "  poll retry $i: status=$ST"
    [[ "$ST" == "awaiting_approval" ]] && break
    sleep 2
  done
fi

echo "==> 4) Approve → ig-publish"
APPROVE=$(curl -sS -X POST "$API/api/v1/approvals/wa-button" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: smoke-approve-$SUFFIX" \
  -d "{\"waUserId\":\"$WA_USER\",\"postId\":\"$POST_ID\",\"button\":\"approve\"}")
echo "$APPROVE" | tee /tmp/rozpost-approve.json

echo "==> 5) Wait for Published + permalink"
for i in $(seq 1 60); do
  POST=$(curl -sS "$API/api/v1/posts/$POST_ID")
  ST=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['status'])" "$POST")
  PERM=$(python3 -c "import json,sys; print(json.loads(sys.argv[1]).get('igPermalink') or '')" "$POST")
  echo "  poll publish $i: status=$ST permalink=$PERM"
  if [[ "$ST" == "published" && -n "$PERM" ]]; then
    echo "$POST" | tee /tmp/rozpost-published.json
    echo "SMOKE OK postId=$POST_ID permalink=$PERM"
    exit 0
  fi
  sleep 2
done

echo "SMOKE FAILED: post did not reach published" >&2
curl -sS "$API/api/v1/posts/$POST_ID" >&2 || true
exit 1
