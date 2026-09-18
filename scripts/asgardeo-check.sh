#!/usr/bin/env bash
# Diagnoses the Asgardeo wiring and shows what is actually inside your token.
#
#   make check-idp          # or: ./scripts/asgardeo-check.sh [user-alias]
#
# Run this before `make demo`. Getting roles into a JWT *access* token is the
# step most Asgardeo setups miss — attributes go into the ID token by default,
# and the resource servers here validate the access token. Rather than have you
# guess, this fetches a real token, decodes it, and says what is missing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/asgardeo-env.sh"

ALIAS="${1:-admin}"
EXPECTED_ROLES="CUSTOMER WAREHOUSE_OPERATOR ADMIN"

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
head_() { printf '\n\033[1m%s\033[0m\n' "$1"; }

command -v python3 >/dev/null || { echo "this script needs python3" >&2; exit 1; }

head_ "1. Configuration"
asgardeo_require_config || exit 1
ok "organisation: ${ASGARDEO_ORG}"
ok "client id:    ${ASGARDEO_CLIENT_ID}"
ok "issuer:       ${ASGARDEO_ISSUER}"

head_ "2. Discovery document"
# Separate the transport outcome from the HTTP status: a DNS or connectivity
# failure is a very different problem from an organisation that does not exist,
# and reporting one as the other sends you looking in the wrong place.
http_code=$(curl -s -o /tmp/freshchain-discovery.json -w '%{http_code}' \
  --max-time 20 "$ASGARDEO_DISCOVERY_URL") && curl_rc=0 || curl_rc=$?

if [ "$curl_rc" -ne 0 ]; then
  bad "could not reach ${ASGARDEO_BASE}"
  echo
  case "$curl_rc" in
    6)  echo "    DNS lookup failed. Check your network or DNS resolver." ;;
    7)  echo "    Connection refused. A proxy or firewall may be blocking outbound HTTPS." ;;
    28) echo "    The request timed out. This is usually a transient network problem;"
        echo "    it does not mean the organisation is wrong. Try again in a moment." ;;
    35) echo "    TLS handshake failed. A TLS-inspecting proxy is the usual cause." ;;
    *)  echo "    curl exited with code ${curl_rc}." ;;
  esac
  echo
  echo "    Verify basic connectivity:"
  echo "      curl -sS -o /dev/null -w '%{http_code}\\n' ${ASGARDEO_DISCOVERY_URL}"
  exit 1
fi

case "$http_code" in
  200)
    if grep -q '"jwks_uri"' /tmp/freshchain-discovery.json; then
      ok "reachable at ${ASGARDEO_DISCOVERY_URL}"
      python3 -c '
import json
d = json.load(open("/tmp/freshchain-discovery.json"))
print("    issuer   :", d.get("issuer"))
print("    jwks_uri :", d.get("jwks_uri"))
grants = d.get("grant_types_supported", [])
print("    grants   :", ", ".join(grants))
if "authorization_code" not in grants:
    print("    NOTE: authorization_code is not advertised, which make login depends on.")
'
    else
      bad "unexpected response from ${ASGARDEO_DISCOVERY_URL}"
      echo "    HTTP 200 but no jwks_uri — a captive portal or proxy may have answered."
      exit 1
    fi
    ;;
  404)
    bad "Asgardeo does not recognise the organisation '${ASGARDEO_ORG}'"
    echo
    if [ "$ASGARDEO_ORG" = "freshchain" ]; then
      echo "    That is still the example placeholder from .env.example."
    fi
    echo "    The handle comes from your console URL:"
    echo "      https://console.asgardeo.io/t/<org>"
    echo "    It is the ORGANISATION name, not the application name."
    echo
    echo "    Test a candidate:"
    echo "      curl -s -o /dev/null -w '%{http_code}\\n' \\"
    echo "        https://api.asgardeo.io/t/<org>/oauth2/token/.well-known/openid-configuration"
    exit 1
    ;;
  *)
    bad "unexpected HTTP ${http_code} from ${ASGARDEO_DISCOVERY_URL}"
    head -c 300 /tmp/freshchain-discovery.json
    exit 1
    ;;
esac

head_ "3. Access token (user alias: ${ALIAS})"
# Deliberately no password grant here: Asgardeo does not offer it on current
# application templates. Tokens come from the authorization-code login cached by
# `make login`.
if ! token="$("$ROOT/scripts/token.sh" "$ALIAS" 2>&1)"; then
  bad "no usable token for '${ALIAS}'"
  printf '    %s\n' "$token"
  echo
  echo "    Sign the demo users in first:"
  echo "      make login"
  echo
  echo "    That opens a browser once per user and caches the tokens under"
  echo "    .tokens/, after which they refresh silently."
  exit 1
fi
ok "got a cached access token for the '${ALIAS}' user"

head_ "4. What the access token actually contains"
printf '%s' "$token" | python3 "$ROOT/scripts/decode-access-token.py" $EXPECTED_ROLES
rc=$?

head_ "Result"
if [ $rc -eq 0 ]; then
  ok "identity is wired up correctly — run 'make seed && make demo'"
else
  bad "fix the above, then run this again"
fi
exit $rc
