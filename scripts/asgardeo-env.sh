#!/usr/bin/env bash
# Shared Asgardeo configuration, sourced by the other scripts.
# Reads .env if present; every value can also be overridden in the environment.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi

ASGARDEO_ORG="${ASGARDEO_ORG:-}"
ASGARDEO_BASE="${ASGARDEO_BASE:-https://api.asgardeo.io}"
ASGARDEO_ISSUER="${ASGARDEO_BASE}/t/${ASGARDEO_ORG}/oauth2/token"
ASGARDEO_TOKEN_URL="${ASGARDEO_ISSUER}"
ASGARDEO_DISCOVERY_URL="${ASGARDEO_ISSUER}/.well-known/openid-configuration"

# `openid` is required; `roles` and `groups` are what surface the authorisation
# claims. An identity provider ignores scopes it does not recognise, so asking
# for both is harmless and saves a round of guessing.
ASGARDEO_SCOPE="${ASGARDEO_SCOPE:-openid profile roles groups}"

asgardeo_require_config() {
  local missing=()
  [ -n "$ASGARDEO_ORG" ] || missing+=("ASGARDEO_ORG")
  [ -n "${ASGARDEO_CLIENT_ID:-}" ] || missing+=("ASGARDEO_CLIENT_ID")
  [ -n "${ASGARDEO_CLIENT_SECRET:-}" ] || missing+=("ASGARDEO_CLIENT_SECRET")
  if [ ${#missing[@]} -gt 0 ]; then
    echo "Missing config: ${missing[*]}" >&2
    echo "Copy .env.example to .env and fill it in — see docs/asgardeo-setup.md" >&2
    return 1
  fi
}

# Resolves a role alias to the username/password pair for that demo user.
asgardeo_user_for() {
  case "$1" in
    customer|alice)    printf '%s\n%s\n' "${FRESHCHAIN_CUSTOMER_USER:-alice}" "${FRESHCHAIN_CUSTOMER_PASSWORD:-}" ;;
    customer2|bob)     printf '%s\n%s\n' "${FRESHCHAIN_SECOND_CUSTOMER_USER:-bob}" "${FRESHCHAIN_SECOND_CUSTOMER_PASSWORD:-}" ;;
    operator|wanda)    printf '%s\n%s\n' "${FRESHCHAIN_OPERATOR_USER:-wanda}" "${FRESHCHAIN_OPERATOR_PASSWORD:-}" ;;
    admin|admin-user)  printf '%s\n%s\n' "${FRESHCHAIN_ADMIN_USER:-freshchain-admin}" "${FRESHCHAIN_ADMIN_PASSWORD:-}" ;;
    *) echo "unknown user alias '$1' (use: customer, customer2, operator, admin)" >&2; return 1 ;;
  esac
}
