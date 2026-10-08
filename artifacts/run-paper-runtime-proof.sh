#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:17080}"
OUTPUT_FILE="${1:-artifacts/paper-runtime-proof.json}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

request() {
    local name="$1"
    shift
    local status
    status="$(curl -sS -o "$TMP_DIR/$name.body" -w '%{http_code}' "$@")"
    printf '%s' "$status" > "$TMP_DIR/$name.status"
}

status_of() { tr -d '\n' < "$TMP_DIR/$1.status"; }
body_of() { cat "$TMP_DIR/$1.body"; }

USERNAME="runtime_paper_$(date +%s)_$RANDOM"
PASSWORD="$(openssl rand -hex 24)"

request register -X POST "$BASE_URL/api/v1/users/register" \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$USERNAME\",\"email\":\"$USERNAME@local.test\",\"password\":\"$PASSWORD\"}"

LOGIN_RESPONSE="$(curl -sS -X POST "$BASE_URL/api/v1/users/login" \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
TOKEN="$(jq -r '.token // empty' <<< "$LOGIN_RESPONSE")"
if [[ -z "$TOKEN" ]]; then
    printf 'Login failed: %s\n' "$LOGIN_RESPONSE" >&2
    exit 1
fi

request eligible -X GET "$BASE_URL/api/v1/risk-profiles/eligible" \
    -H "Authorization: Bearer $TOKEN"
PROFILE_ID="$(jq -r '.[0].profileId' "$TMP_DIR/eligible.body")"
PROFILE_VERSION="$(jq -r '.[0].semanticVersion' "$TMP_DIR/eligible.body")"

request broker_account -X POST "$BASE_URL/api/v1/broker-accounts" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    --data "{\"provider\":\"KRAKEN\",\"displayName\":\"Runtime PAPER $(date +%Y%m%d%H%M%S)\",\"executionMode\":\"PAPER\",\"initialCapital\":10000,\"riskProfile\":{\"profileId\":\"$PROFILE_ID\",\"semanticVersion\":\"$PROFILE_VERSION\"}}"
BROKER_ACCOUNT_ID="$(jq -r '.id' "$TMP_DIR/broker_account.body")"

request accounts -X GET "$BASE_URL/api/v1/accounts" \
    -H "Authorization: Bearer $TOKEN"
ACCOUNT_ID="$(jq -r --arg broker "$BROKER_ACCOUNT_ID" '.[] | select(.brokerAccountId == $broker) | .accountId' "$TMP_DIR/accounts.body")"
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "null" ]]; then
    printf 'PAPER financial account was not created for broker account %s\n' "$BROKER_ACCOUNT_ID" >&2
    exit 1
fi

request scope -X POST "$BASE_URL/api/v1/intelligence/scans/scope" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    --data "{\"accountId\":\"$ACCOUNT_ID\",\"objective\":\"runtime PAPER risk proof\",\"requestedMarketIds\":null,\"scopeMode\":\"ALL_ELIGIBLE\"}"

SCAN_KEY="runtime-scan-$USERNAME"
request scan -X POST "$BASE_URL/api/v1/intelligence/scans" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $SCAN_KEY" \
    --data "{\"accountId\":\"$ACCOUNT_ID\",\"objective\":\"runtime PAPER risk proof\",\"requestedMarketIds\":null,\"scopeMode\":\"ALL_ELIGIBLE\"}"
SCAN_ID="$(jq -r '.scanId' "$TMP_DIR/scan.body")"

for attempt in $(seq 1 30); do
    request scan_state -X GET "$BASE_URL/api/v1/intelligence/scans/$SCAN_ID" \
        -H "Authorization: Bearer $TOKEN"
    SCAN_STATE="$(jq -r '.status // .state // .phase // empty' "$TMP_DIR/scan_state.body")"
    case "$SCAN_STATE" in
        COMPLETED|SUCCEEDED|FAILED|REJECTED|CANCELLED) break ;;
    esac
    sleep 2
done

request opportunities -X GET "$BASE_URL/api/v1/opportunities/active" \
    -H "Authorization: Bearer $TOKEN"
OPPORTUNITY_ID="$(jq -r 'map(select((.direction // .tradeDirection // "") == "LONG" and ((.instrument // "") | endswith("/USD")))) | .[0].id // .[0].opportunityId // empty' "$TMP_DIR/opportunities.body")"
if [[ -z "$OPPORTUNITY_ID" || "$OPPORTUNITY_ID" == "null" ]]; then
    printf 'No active opportunity was produced by scan %s\n' "$SCAN_ID" >&2
    exit 1
fi

PLAN_KEY="runtime-plan-$USERNAME"
request plan -X POST "$BASE_URL/api/v1/trade-plans/opportunities/$OPPORTUNITY_ID/trade-plans" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $PLAN_KEY" \
    --data "{\"accountId\":\"$ACCOUNT_ID\"}"
TRADE_PLAN_ID="$(jq -r '.tradePlanId // empty' "$TMP_DIR/plan.body")"
TRADE_PLAN_VERSION="$(jq -r '.tradePlanVersion // 1' "$TMP_DIR/plan.body")"

request decision -X POST "$BASE_URL/api/v1/trade-plans/$TRADE_PLAN_ID/versions/$TRADE_PLAN_VERSION/decisions" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    --data '{"decision":"ACCEPT"}'

TRADE_PLAN_VERSION="$(jq -r '.version // .tradePlanVersion // (.versionNumber // 0)' "$TMP_DIR/decision.body")"
if [[ "$TRADE_PLAN_VERSION" == "0" || "$TRADE_PLAN_VERSION" == "null" ]]; then
    TRADE_PLAN_VERSION="2"
fi

RISK_KEY="runtime-risk-$USERNAME"
request risk -X POST "$BASE_URL/api/v1/trade-plans/$TRADE_PLAN_ID/versions/$TRADE_PLAN_VERSION/risk-evaluations" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $RISK_KEY" \
    --data "{\"accountId\":\"$ACCOUNT_ID\"}"
EVALUATION_ID="$(jq -r '.evaluationId // empty' "$TMP_DIR/risk.body")"
APPROVED="$(jq -r '.approved // false' "$TMP_DIR/risk.body")"

EXECUTION_ID=""
EXECUTION_STATUS="not_attempted"
if [[ "$APPROVED" == "true" ]]; then
    EXPIRES_AT="$(date -u -d '+1 hour' '+%Y-%m-%dT%H:%M:%SZ')"
    request execution_validate -X POST "$BASE_URL/api/v1/executions/validate" \
        -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' \
        -H "Idempotency-Key: runtime-execution-$USERNAME" \
        --data "{\"tradePlanId\":\"$TRADE_PLAN_ID\",\"tradePlanVersion\":$TRADE_PLAN_VERSION,\"evaluationId\":\"$EVALUATION_ID\",\"brokerAccountId\":\"$BROKER_ACCOUNT_ID\",\"expiresAt\":\"$EXPIRES_AT\"}"
    EXECUTION_ID="$(jq -r '.id // .executionId // empty' "$TMP_DIR/execution_validate.body")"
    if [[ -n "$EXECUTION_ID" ]]; then
        request execution -X POST "$BASE_URL/api/v1/executions/$EXECUTION_ID/execute" \
            -H "Authorization: Bearer $TOKEN"
        EXECUTION_STATUS="$(jq -r '.status // .state // "unknown"' "$TMP_DIR/execution.body")"
    else
        EXECUTION_STATUS="validation_failed"
    fi
fi

RISK_RESPONSE="$(jq -c . "$TMP_DIR/risk.body")"

mkdir -p "$(dirname "$OUTPUT_FILE")"
jq -n \
    --arg generatedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    --arg baseUrl "$BASE_URL" \
    --arg username "$USERNAME" \
    --arg registerStatus "$(status_of register)" \
    --arg profileId "$PROFILE_ID" \
    --arg profileVersion "$PROFILE_VERSION" \
    --arg brokerAccountId "$BROKER_ACCOUNT_ID" \
    --arg accountId "$ACCOUNT_ID" \
    --arg scopeStatus "$(status_of scope)" \
    --arg scanStatus "$(status_of scan)" \
    --arg scanId "$SCAN_ID" \
    --arg scanState "$SCAN_STATE" \
    --arg opportunityId "$OPPORTUNITY_ID" \
    --arg planStatus "$(status_of plan)" \
    --arg tradePlanId "$TRADE_PLAN_ID" \
    --argjson tradePlanVersion "$TRADE_PLAN_VERSION" \
    --arg decisionStatus "$(status_of decision)" \
    --arg riskStatus "$(status_of risk)" \
    --arg evaluationId "$EVALUATION_ID" \
    --arg approved "$APPROVED" \
    --arg riskResponse "$RISK_RESPONSE" \
    --arg executionId "$EXECUTION_ID" \
    --arg executionStatus "$EXECUTION_STATUS" \
    '{generatedAt: $generatedAt, baseUrl: $baseUrl, username: $username, registration: {status: ($registerStatus | tonumber)}, profile: {id: $profileId, version: $profileVersion}, paper: {brokerAccountId: $brokerAccountId, accountId: $accountId}, scan: {status: ($scanStatus | tonumber), id: $scanId, state: $scanState, scopeStatus: ($scopeStatus | tonumber)}, opportunityId: $opportunityId, tradePlan: {status: ($planStatus | tonumber), id: $tradePlanId, version: $tradePlanVersion, decisionStatus: ($decisionStatus | tonumber)}, risk: {status: ($riskStatus | tonumber), evaluationId: $evaluationId, approved: ($approved == "true"), response: ($riskResponse | fromjson)}, execution: {id: $executionId, status: $executionStatus}}' \
    > "$OUTPUT_FILE"

jq . "$OUTPUT_FILE"
