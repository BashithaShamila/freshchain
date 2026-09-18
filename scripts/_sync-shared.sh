#!/usr/bin/env bash
# The outbox, the idempotency guard and the envelope reader are the same
# mechanism in every service, but each service owns its own copy: they are
# independently deployable and must not share a runtime library that would let
# one service's upgrade force another's. This script keeps the copies honest.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/inventory-service/src/main/java/com/freshchain/inventory"

FILES=(
  support/OutboxEntry.java
  support/ProcessedEvent.java
  support/IdempotencyGuard.java
  support/OutboxWriter.java
  support/OrderMdc.java
  repository/OutboxRepository.java
  repository/ProcessedEventRepository.java
  messaging/EventReader.java
  messaging/OutboxPublisher.java
  config/OutboxProperties.java
  config/JwtRoleConverter.java
  config/JwtDecoderConfig.java
  config/SecurityProperties.java
  config/JacksonConfig.java
)

for target in order fulfillment; do
  case "$target" in
    order)       module="order-service" ;;
    fulfillment) module="fulfillment-service" ;;
  esac
  dest="$ROOT/$module/src/main/java/com/freshchain/$target"
  for file in "${FILES[@]}"; do
    mkdir -p "$dest/$(dirname "$file")"
    sed "s/com\.freshchain\.inventory/com.freshchain.$target/g" "$SRC/$file" > "$dest/$file"
  done
  echo "synced ${#FILES[@]} shared files into $module"
done
