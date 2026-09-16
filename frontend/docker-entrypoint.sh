#!/bin/sh
# Writes the runtime configuration the SPA reads on boot.
#
# Vite bakes import.meta.env values in at build time, which would mean one image
# per environment. Generating this file at container start instead lets the same
# image run locally and in any deployment, configured purely by environment.
set -eu

CONFIG_FILE=/usr/share/nginx/html/config.js

cat > "$CONFIG_FILE" <<JS
window.__FRESHCHAIN_CONFIG__ = {
  asgardeoBaseUrl: "${ASGARDEO_BASE_URL:-}",
  asgardeoClientId: "${ASGARDEO_SPA_CLIENT_ID:-}",
  apiBaseUrl: "${API_BASE_URL:-}",
  appBaseUrl: "${APP_BASE_URL:-}"
};
JS

if [ -z "${ASGARDEO_SPA_CLIENT_ID:-}" ]; then
  echo "WARNING: ASGARDEO_SPA_CLIENT_ID is not set; sign-in will fail." >&2
  echo "         Set it in .env, then recreate this container:" >&2
  echo "           docker compose --profile apps up -d frontend" >&2
  echo "         Compose does not restart a container when .env changes." >&2
fi

echo "frontend configured for org base '${ASGARDEO_BASE_URL:-unset}', api '${API_BASE_URL:-same-origin}'"
exec "$@"
