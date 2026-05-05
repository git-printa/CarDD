#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[e2e] starting compose services (ai-service + backend)..."
docker compose up -d --build ai-service backend

cleanup() {
  echo "[e2e] leaving services running (no auto-down)"
}
trap cleanup EXIT

wait_for_health() {
  local url="$1"
  local name="$2"
  echo "[e2e] waiting for $name at $url"
  for i in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "[e2e] $name is healthy"
      return 0
    fi
    sleep 2
  done
  echo "[e2e] timeout waiting for $name"
  return 1
}

wait_for_health "http://localhost:${AI_SERVICE_PORT:-8000}/health" "ai-service"
wait_for_health "http://localhost:${BACKEND_PORT:-8080}/health" "backend"

echo "[e2e] generating a tiny JPEG for upload"
TMP_BASE="$(mktemp -t car-damage-e2e)"
TMP_IMG="${TMP_BASE}.jpg"
rm -f "$TMP_BASE"
python3 - <<PY
from PIL import Image
Image.new("RGB", (320, 240), color=(140, 80, 90)).save("$TMP_IMG", format="JPEG", quality=85)
print("created", "$TMP_IMG")
PY

if [ ! -f "$TMP_IMG" ]; then
  echo "[e2e] expected temp image was not created"
  exit 1
fi

echo "[e2e] calling backend upload endpoint"
RESP_FILE="$(mktemp -t car-damage-e2e-resp)"
HTTP_CODE="$(curl -sS -o "$RESP_FILE" -w "%{http_code}" -X POST "http://localhost:${BACKEND_PORT:-8080}/api/v1/assessments/upload" \
  -F "vehicleId=E2E-TEST" \
  -F "files=@${TMP_IMG};type=image/jpeg")"

if [ "$HTTP_CODE" != "200" ]; then
  echo "[e2e] upload failed with HTTP $HTTP_CODE"
  cat "$RESP_FILE"
  rm -f "$TMP_IMG" "$RESP_FILE"
  exit 1
fi

BATCH_ID="$(python3 - <<PY
import json
from pathlib import Path
payload = json.loads(Path("$RESP_FILE").read_text())
assert payload["vehicleId"] == "E2E-TEST", "vehicleId mismatch"
assert payload["totalFiles"] >= 1, "expected at least one uploaded file"
assert payload["items"], "items should not be empty"
assert payload["items"][0]["status"] == "UPLOADED", "unexpected item status"
print(payload["batchId"])
PY
)"

echo "[e2e] upload response valid, batchId=$BATCH_ID"

echo "[e2e] calling backend analyze endpoint"
ANALYZE_FILE="$(mktemp -t car-damage-analyze-resp)"
ANALYZE_CODE="$(curl -sS -o "$ANALYZE_FILE" -w "%{http_code}" -X POST "http://localhost:${BACKEND_PORT:-8080}/api/v1/assessments/${BATCH_ID}/analyze")"

if [ "$ANALYZE_CODE" != "200" ]; then
  echo "[e2e] analyze failed with HTTP $ANALYZE_CODE"
  cat "$ANALYZE_FILE"
  rm -f "$TMP_IMG" "$RESP_FILE" "$ANALYZE_FILE"
  exit 1
fi

python3 - <<PY
import json
from pathlib import Path
res = json.loads(Path("$ANALYZE_FILE").read_text())
assert "damages" in res, "missing damages"
assert "total_cost" in res, "missing total_cost"
assert isinstance(res["damages"], list), "damages should be list"
print("[e2e] analyze response valid, total_cost=", res["total_cost"])
PY

rm -f "$TMP_IMG" "$RESP_FILE" "$ANALYZE_FILE"
echo "[e2e] smoke test passed"
