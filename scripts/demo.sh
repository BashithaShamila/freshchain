#!/usr/bin/env bash
# End-to-end walkthrough of the whole system, in five acts.
#
#   1. FEFO allocation across two lots with different expiry dates
#   2. Confirm, pick, ship, and watch on-hand finally drop
#   3. Cancellation, and the compensating release of held stock
#   4. Partial fulfilment, with substitution advice on the short line
#   5. The ownership rule: one customer cannot read another's order
#
# Run `make infra && make up && make seed` first.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Sourced for GATEWAY_PORT and friends: the published ports are configurable so
# this project can coexist with whatever else is running on the machine.
# shellcheck disable=SC1091
. "$ROOT/scripts/asgardeo-env.sh"
GATEWAY="${GATEWAY_URL:-http://localhost:${GATEWAY_PORT:-8080}}"
WAREHOUSE="${WAREHOUSE_ID:-WH-COL-01}"
SKU=CHK-BRST-5LB

# Three identities from Asgardeo: a customer, a second customer (to prove the
# ownership rule), and a warehouse operator.
ALICE="$("$ROOT/scripts/token.sh" customer)"
BOB="$("$ROOT/scripts/token.sh" customer2)"
WANDA="$("$ROOT/scripts/token.sh" operator)"

bold() { printf '\n\033[1m%s\033[0m\n' "$1"; }
note() { printf '  %s\n' "$1"; }

api() { # api METHOD PATH TOKEN [BODY]
  local method="$1" path="$2" token="$3" body="${4:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" "${GATEWAY}${path}" \
      -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "$body"
  else
    curl -sS -X "$method" "${GATEWAY}${path}" -H "Authorization: Bearer ${token}"
  fi
}

status_of() { api GET "/api/v1/orders/$1" "$2" | jq -r '.status'; }

# Allocation is asynchronous, so the demo waits for it rather than sleeping and
# hoping. This is the same thing a client would do.
wait_for_status() { # wait_for_status ORDER_ID TOKEN EXPECTED...
  local order="$1" token="$2"; shift 2
  for _ in $(seq 1 40); do
    local current; current=$(status_of "$order" "$token")
    for expected in "$@"; do
      [ "$current" = "$expected" ] && { echo "$current"; return 0; }
    done
    sleep 0.25
  done
  echo "TIMEOUT(last=$(status_of "$order" "$token"))"
  return 1
}

place() { # place TOKEN QTY ALLOW_PARTIAL
  api POST /api/v1/orders "$1" \
    "{\"warehouseId\":\"${WAREHOUSE}\",\"allowPartial\":$3,\"lines\":[{\"sku\":\"${SKU}\",\"qty\":$2}]}"
}

available() { api GET "/api/v1/inventory/${SKU}/availability?warehouseId=${WAREHOUSE}" "$ALICE"; }

command -v jq >/dev/null || { echo "this script needs jq installed" >&2; exit 1; }

# The demo allocates real stock, so it needs some. A previous demo or load run
# will have consumed it; say so plainly rather than failing halfway through.
STOCK=$(available | jq -r '.available')
if [ "${STOCK:-0}" -lt 120 ]; then
  echo "Only ${STOCK:-0} cases of ${SKU} available; this walkthrough needs at least 120." >&2
  echo "Run 'make seed' to receive more stock, then try again." >&2
  exit 1
fi

# ---------------------------------------------------------------- act one ---
bold "1. FEFO allocation"
available | jq -r '"  on hand \(.available) across \(.lots | length) lot(s); nearest expiry \(.nearestExpiry)"'

ORDER_A=$(place "$ALICE" 40 false | jq -r '.order.orderId')
note "placed order $ORDER_A for 40 cases (202 Accepted — allocation is asynchronous)"
STATUS_A=$(wait_for_status "$ORDER_A" "$ALICE" ALLOCATED PARTIALLY_ALLOCATED REJECTED)
note "status: ${STATUS_A}"

if [ "$STATUS_A" = "REJECTED" ]; then
  echo "Allocation was rejected — there is not enough unexpired stock to demo with." >&2
  echo "Run 'make seed' and try again." >&2
  exit 1
fi

api GET "/api/v1/reservations/${ORDER_A}" "$ALICE" \
  | jq -r '(.lines // [])[] | "  drew \(.qty) from lot \(.lotId)"'
note "the near-expiry lot is drained first — that is FEFO doing its job"

# ---------------------------------------------------------------- act two ---
bold "2. Confirm, pick, ship"
BEFORE=$(available | jq -r '.totalOnHand')
api POST "/api/v1/orders/${ORDER_A}/confirm" "$ALICE" > /dev/null
note "confirmed; on hand is still ${BEFORE} because nothing has physically moved"

sleep 2
api POST "/api/v1/shipments/${ORDER_A}/pick" "$WANDA" | jq -r '"  picked: \(.status)"'
api POST "/api/v1/shipments/${ORDER_A}/ship" "$WANDA" | jq -r '"  shipped: \(.status), tracking \(.trackingRef)"'
note "order status: $(wait_for_status "$ORDER_A" "$ALICE" SHIPPED)"
sleep 2
available | jq -r '"  on hand is now \(.totalOnHand) — dropped only when stock left the building"'

# -------------------------------------------------------------- act three ---
bold "3. Cancellation and compensation"
HELD_BEFORE=$(available | jq -r '.totalReserved')
ORDER_B=$(place "$ALICE" 20 false | jq -r '.order.orderId')
wait_for_status "$ORDER_B" "$ALICE" ALLOCATED PARTIALLY_ALLOCATED REJECTED > /dev/null
note "placed order $ORDER_B; reserved is now $(available | jq -r '.totalReserved')"

api POST "/api/v1/orders/${ORDER_B}/cancel" "$ALICE" \
  '{"reason":"customer changed their delivery date"}' > /dev/null
sleep 3
note "cancelled; reserved is back to $(available | jq -r '.totalReserved') (was ${HELD_BEFORE} before the order)"

# --------------------------------------------------------------- act four ---
bold "4. Partial fulfilment and substitution advice"
HUGE=$(( $(available | jq -r '.available') + 500 ))
ORDER_C=$(place "$ALICE" "$HUGE" true | jq -r '.order.orderId')
note "asked for ${HUGE} cases, far more than exists, with allowPartial=true"
note "status: $(wait_for_status "$ORDER_C" "$ALICE" PARTIALLY_ALLOCATED REJECTED)"
api GET "/api/v1/orders/${ORDER_C}" "$ALICE" \
  | jq -r '(.lines // [])[] | "  allocated \(.qtyAllocated) of \(.qtyRequested) requested"'
api GET "/api/v1/orders/${ORDER_C}" "$ALICE" | jq -r '
  if (.substitutions // []) | length > 0
  then .substitutions[] | "  suggested \(.suggestedSku) (\(.suggestedName)) — \(.rationale)"
  else "  no substitutes available with matching allergens" end'

# --------------------------------------------------------------- act five ---
bold "5. Ownership is enforced, not assumed"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' \
  "${GATEWAY}/api/v1/orders/${ORDER_A}" -H "Authorization: Bearer ${BOB}")
note "Bob reading Alice's order: HTTP ${CODE} (403 expected — authenticated, but not his)"

CODE=$(curl -sS -o /dev/null -w '%{http_code}' \
  -X POST "${GATEWAY}/api/v1/lots" -H "Authorization: Bearer ${ALICE}" \
  -H 'Content-Type: application/json' \
  -d "{\"sku\":\"${SKU}\",\"warehouseId\":\"${WAREHOUSE}\",\"qty\":1,\"expiryDate\":\"2030-01-01\"}")
note "Alice receiving stock: HTTP ${CODE} (403 expected — that is an admin operation)"

bold "Done."
note "Grafana  http://localhost:3000  (admin/admin)"
note "Swagger  http://localhost:8081/swagger-ui.html"
