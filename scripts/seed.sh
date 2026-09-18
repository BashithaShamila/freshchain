#!/usr/bin/env bash
# Receives demo stock into the inventory service.
#
# Deliberately does this through the API rather than with a SQL insert, so the
# receiving path — validation, lot creation, the expiry guard — is exercised
# rather than bypassed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Sourced for GATEWAY_PORT and friends: the published ports are configurable so
# this project can coexist with whatever else is running on the machine.
# shellcheck disable=SC1091
. "$ROOT/scripts/asgardeo-env.sh"
GATEWAY="${GATEWAY_URL:-http://localhost:${GATEWAY_PORT:-8080}}"
WAREHOUSE="${WAREHOUSE_ID:-WH-COL-01}"

TOKEN="$("$ROOT/scripts/token.sh" admin)"

receive() {
  local sku="$1" qty="$2" days="$3"
  local expiry
  expiry=$(date -u -v+"${days}"d +%Y-%m-%d 2>/dev/null || date -u -d "+${days} days" +%Y-%m-%d)

  local code
  code=$(curl -sS -o /tmp/freshchain-seed.json -w '%{http_code}' \
    -X POST "${GATEWAY}/api/v1/lots" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"${sku}\",\"warehouseId\":\"${WAREHOUSE}\",\"qty\":${qty},\"expiryDate\":\"${expiry}\"}")

  if [ "$code" = "201" ]; then
    printf '  %-14s %4s cases  expires %s\n' "$sku" "$qty" "$expiry"
  else
    printf '  %-14s FAILED (HTTP %s): %s\n' "$sku" "$code" "$(cat /tmp/freshchain-seed.json)" >&2
  fi
}

echo "Receiving stock into ${WAREHOUSE}:"
# Chicken breast arrives as two lots with different dates, which is what makes
# the FEFO demo visible: the near-expiry lot has to drain first.
receive CHK-BRST-5LB  25 3
receive CHK-BRST-5LB 100 30
receive CHK-THGH-5LB  60 6
receive CHK-TNDR-4LB  40 5
receive TKY-BRST-5LB  30 8
receive BEF-GRND-10LB 80 4
receive SLM-FLLT-4LB  15 3
receive MLK-WHL-4GAL  50 11
receive CHZ-MOZZ-5LB  70 25
receive LET-ROM-24CT  45 8
receive BRD-BRGR-96CT 90 4
receive BRD-SUB-72CT  60 4
echo "Done."
