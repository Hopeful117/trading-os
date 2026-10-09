#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:17080}"
USERNAME="manual_idempotency_$(date +%s)_$RANDOM"
PASSWORD="$(openssl rand -hex 24)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

request() {
    local name="$1"
    shift
    curl -sS -o "$TMP_DIR/$name.body" -w '%{http_code}' "$@" > "$TMP_DIR/$name.status"
}

status_of() { tr -d '\n' < "$TMP_DIR/$1.status"; }
body_of() { cat "$TMP_DIR/$1.body"; }
require_status() {
    local name="$1"
    local expected="$2"
    local actual
    actual="$(status_of "$name")"
    if [[ ! "$actual" =~ $expected ]]; then
        printf '%s failed (%s): %s\n' "$name" "$actual" "$(body_of "$name")" >&2
        exit 1
    fi
}

request register -X POST "$BASE_URL/api/v1/users/register" \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$USERNAME\",\"email\":\"$USERNAME@local.test\",\"password\":\"$PASSWORD\"}"
require_status register '^2[0-9][0-9]$'

LOGIN_RESPONSE="$(curl -sS -X POST "$BASE_URL/api/v1/users/login" \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
TOKEN="$(jq -r '.token // empty' <<< "$LOGIN_RESPONSE")"
[[ -n "$TOKEN" ]] || { printf 'Login failed: %s\n' "$LOGIN_RESPONSE" >&2; exit 1; }

request eligible -X GET "$BASE_URL/api/v1/risk-profiles/eligible" \
    -H "Authorization: Bearer $TOKEN"
require_status eligible '^2[0-9][0-9]$'
PROFILE_ID="$(jq -r '.[0].profileId' "$TMP_DIR/eligible.body")"
PROFILE_VERSION="$(jq -r '.[0].semanticVersion' "$TMP_DIR/eligible.body")"

request broker_account -X POST "$BASE_URL/api/v1/broker-accounts" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    --data "{\"provider\":\"KRAKEN\",\"displayName\":\"Manual idempotency E2E\",\"executionMode\":\"PAPER\",\"initialCapital\":10000,\"riskProfile\":{\"profileId\":\"$PROFILE_ID\",\"semanticVersion\":\"$PROFILE_VERSION\"}}"
require_status broker_account '^2[0-9][0-9]$'
BROKER_ACCOUNT_ID="$(jq -r '.id' "$TMP_DIR/broker_account.body")"

request accounts -X GET "$BASE_URL/api/v1/accounts" -H "Authorization: Bearer $TOKEN"
require_status accounts '^2[0-9][0-9]$'
ACCOUNT_ID="$(jq -r --arg broker "$BROKER_ACCOUNT_ID" '.[] | select(.brokerAccountId == $broker) | .accountId' "$TMP_DIR/accounts.body")"
[[ -n "$ACCOUNT_ID" && "$ACCOUNT_ID" != "null" ]] || { printf 'Account was not created\n' >&2; exit 1; }

request markets -X GET "$BASE_URL/api/v1/markets" -H "Authorization: Bearer $TOKEN"
require_status markets '^2[0-9][0-9]$'
MARKET_ID="$(jq -r 'map(select(.marketState.isTradable == true or .tradable == true))[0].marketId // map(select(.marketId != null))[0].marketId // empty' "$TMP_DIR/markets.body")"
[[ -n "$MARKET_ID" && "$MARKET_ID" != "null" ]] || { printf 'No market was available\n' >&2; exit 1; }

request market -X GET "$BASE_URL/api/v1/markets/$MARKET_ID" -H "Authorization: Bearer $TOKEN"
require_status market '^2[0-9][0-9]$'
REFERENCE_PRICE="$(jq -r '.marketState.lastPrice // .marketState.price // .lastPrice // empty' "$TMP_DIR/market.body")"
[[ -n "$REFERENCE_PRICE" && "$REFERENCE_PRICE" != "null" ]] || REFERENCE_PRICE="100"

PAYLOAD="$(jq -n --arg account "$ACCOUNT_ID" --arg market "$MARKET_ID" --arg price "$REFERENCE_PRICE" \
    '{accountId:$account,marketId:$market,direction:"LONG",entryType:"MARKET",referencePrice:($price|tonumber),quantity:0.001,monetaryRisk:1,thesis:"Manual idempotency E2E",confirmationConditions:["runtime confirmation"],invalidationConditions:["runtime invalidation"],managementRules:[]}')"
KEY="manual-idempotency-$USERNAME"

request first -X POST "$BASE_URL/api/v1/trade-plans/manual" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $KEY" --data "$PAYLOAD"
require_status first '^201$'
PLAN_ID="$(jq -r '.tradePlanId' "$TMP_DIR/first.body")"
PLAN_VERSION="$(jq -r '.tradePlanVersion' "$TMP_DIR/first.body")"

request replay -X POST "$BASE_URL/api/v1/trade-plans/manual" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $KEY" --data "$PAYLOAD"
require_status replay '^201$'
REPLAY_ID="$(jq -r '.tradePlanId' "$TMP_DIR/replay.body")"
REPLAY_VERSION="$(jq -r '.tradePlanVersion' "$TMP_DIR/replay.body")"
[[ "$PLAN_ID" == "$REPLAY_ID" && "$PLAN_VERSION" == "$REPLAY_VERSION" ]] || {
    printf 'Replay returned a different plan: first=%s/%s replay=%s/%s\n' \
        "$PLAN_ID" "$PLAN_VERSION" "$REPLAY_ID" "$REPLAY_VERSION" >&2
    exit 1
}

CONFLICT_PAYLOAD="$(jq '.thesis = "Changed manual idempotency E2E"' <<< "$PAYLOAD")"
request conflict -X POST "$BASE_URL/api/v1/trade-plans/manual" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $KEY" --data "$CONFLICT_PAYLOAD"
require_status conflict '^409$'
[[ "$(jq -r '.code // empty' "$TMP_DIR/conflict.body")" == "IDEMPOTENCY_CONFLICT" ]] || {
    printf 'Unexpected conflict response: %s\n' "$(body_of conflict)" >&2
    exit 1
}

printf 'manual trade-plan idempotency E2E passed: plan=%s/%s\n' "$PLAN_ID" "$PLAN_VERSION"
