#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
EXAMPLE_FILE="$ROOT_DIR/.env.example"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$EXAMPLE_FILE" "$ENV_FILE"
  printf 'Created %s\n' "$ENV_FILE"
fi

chmod 600 "$ENV_FILE"

read_env_value() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ENV_FILE"
}

set_generated_secret() {
  local key="$1"
  local current
  local value
  local temporary_file

  current="$(read_env_value "$key")"
  if [[ -n "$current" ]]; then
    return
  fi

  value="$(openssl rand -base64 32 | tr -d '\n')"
  temporary_file="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { updated = 0 }
    $1 ~ ("^" key "=") {
      print key "=" value
      updated = 1
      next
    }
    { print }
    END {
      if (!updated) print key "=" value
    }
  ' "$ENV_FILE" > "$temporary_file"
  mv "$temporary_file" "$ENV_FILE"
  printf 'Generated %s\n' "$key"
}

validate_secret() {
  local key="$1"
  local value
  local decoded_file
  local byte_count

  value="$(read_env_value "$key")"
  decoded_file="$(mktemp)"
  if ! printf '%s' "$value" | base64 --decode > "$decoded_file" 2>/dev/null; then
    rm -f "$decoded_file"
    printf 'Invalid Base64 value for %s\n' "$key" >&2
    exit 1
  fi

  byte_count="$(wc -c < "$decoded_file")"
  rm -f "$decoded_file"
  if [[ "$byte_count" -ne 32 ]]; then
    printf '%s must decode to exactly 32 bytes\n' "$key" >&2
    exit 1
  fi
}

for key in \
  JWT_SECRET \
  TRADING_CORE_SERVICE_JWT_SECRET \
  BROKER_SERVICE_SERVICE_JWT_SECRET \
  MARKET_INTELLIGENCE_SERVICE_JWT_SECRET \
  TRADING_CORE_MI_SERVICE_JWT_SECRET \
  TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET; do
  set_generated_secret "$key"
done

declare -A seen_values=()
for key in \
  JWT_SECRET \
  TRADING_CORE_SERVICE_JWT_SECRET \
  BROKER_SERVICE_SERVICE_JWT_SECRET \
  MARKET_INTELLIGENCE_SERVICE_JWT_SECRET \
  TRADING_CORE_MI_SERVICE_JWT_SECRET \
  TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET; do
  validate_secret "$key"
  value="$(read_env_value "$key")"
  if [[ -n "${seen_values[$value]:-}" ]]; then
    printf 'JWT secrets must be distinct; duplicate value found at %s\n' "$key" >&2
    exit 1
  fi
  seen_values["$value"]="$key"
done

printf 'Local JWT configuration is ready in %s (mode 600).\n' "$ENV_FILE"
