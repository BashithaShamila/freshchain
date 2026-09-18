#!/usr/bin/env bash
# Puts a demo user's access token on the clipboard, ready to paste into
# Swagger UI's Authorize dialog.
#
#   ./scripts/copy-token.sh customer     CUSTOMER
#   ./scripts/copy-token.sh customer2    CUSTOMER (a second person)
#   ./scripts/copy-token.sh operator     WAREHOUSE_OPERATOR
#   ./scripts/copy-token.sh admin        ADMIN
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALIAS="${1:-customer}"

token="$("$ROOT/scripts/token.sh" "$ALIAS")"

if command -v pbcopy >/dev/null; then
  printf '%s' "$token" | pbcopy
  copied=" (copied to clipboard)"
elif command -v xclip >/dev/null; then
  printf '%s' "$token" | xclip -selection clipboard
  copied=" (copied to clipboard)"
else
  copied=""
fi

python3 - "$token" "$ALIAS" "$copied" <<'PYEOF'
import base64, json, sys, time
token, alias, copied = sys.argv[1], sys.argv[2], sys.argv[3]
claims = json.loads(base64.urlsafe_b64decode(token.split(".")[1] + "==="))
roles = [r for r in (claims.get("roles") or []) if r != "everyone"]
minutes = int(claims.get("exp", 0) - time.time()) // 60
print(f"\n  {alias}: roles {', '.join(roles) or '(none)'}, valid {minutes} more minutes{copied}")
print(f"\n  Paste into Swagger UI -> Authorize -> bearer-jwt (the word 'Bearer' is added for you):\n")
print(f"  {token[:48]}...{token[-12:]}\n")
PYEOF
