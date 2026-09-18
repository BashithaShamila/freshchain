#!/usr/bin/env bash
# Prints an Asgardeo access token for one of the demo users.
#
#   ./scripts/token.sh customer    -> CUSTOMER
#   ./scripts/token.sh customer2   -> CUSTOMER (a different person, for the 403 demo)
#   ./scripts/token.sh operator    -> WAREHOUSE_OPERATOR
#   ./scripts/token.sh admin       -> ADMIN
#
# Reads from the token cache and refreshes silently when needed. It never opens
# a browser, so it is safe to call from inside another script — if no usable
# token exists it fails and tells you to run `make login`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/scripts/asgardeo-login.py" --alias "${1:-customer}" --print
