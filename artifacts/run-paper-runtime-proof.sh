#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:17080}"
OUTPUT_FILE="${1:-artifacts/paper-runtime-proof.json}"
EXECUTE_PAPER="${EXECUTE_PAPER:-false}"
REFRESH_MARKETS="${REFRESH_MARKETS:-false}"
STRICT_PAPER_ENTRY="${STRICT_PAPER_ENTRY:-false}"
ALLOW_PAPER_SHORT="${ALLOW_PAPER_SHORT:-false}"
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
if [[ "$REFRESH_MARKETS" == "true" ]]; then
    request market_sync -X POST "$BASE_URL/api/v1/markets/synchronize" \
        -H "Authorization: Bearer $TOKEN"
    MARKET_SYNC_STATUS="$(status_of market_sync)"
    if [[ ! "$MARKET_SYNC_STATUS" =~ ^2[0-9][0-9]$ ]]; then
        printf 'Market synchronization failed (%s): %s\n' "$MARKET_SYNC_STATUS" \
            "$(body_of market_sync)" >&2
        exit 1
    fi
fi

request eligible -X GET "$BASE_URL/api/v1/risk-profiles/eligible" \
    -H "Authorization: Bearer $TOKEN"
require_status eligible '^2[0-9][0-9]$'
PROFILE_ID="$(jq -r '.[0].profileId' "$TMP_DIR/eligible.body")"
PROFILE_VERSION="$(jq -r '.[0].semanticVersion' "$TMP_DIR/eligible.body")"
PROFILE_RULES="$(jq -c '.[0].rules' "$TMP_DIR/eligible.body")"
[[ -n "$PROFILE_ID" && "$PROFILE_ID" != "null" ]] || { printf 'No eligible risk profile returned\n' >&2; exit 1; }

request broker_account -X POST "$BASE_URL/api/v1/broker-accounts" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    --data "{\"provider\":\"KRAKEN\",\"displayName\":\"Runtime PAPER $(date +%Y%m%d%H%M%S)\",\"executionMode\":\"PAPER\",\"initialCapital\":10000,\"riskProfile\":{\"profileId\":\"$PROFILE_ID\",\"semanticVersion\":\"$PROFILE_VERSION\"}}"
require_status broker_account '^2[0-9][0-9]$'
BROKER_ACCOUNT_ID="$(jq -r '.id' "$TMP_DIR/broker_account.body")"
[[ -n "$BROKER_ACCOUNT_ID" && "$BROKER_ACCOUNT_ID" != "null" ]] || { printf 'PAPER broker account creation returned no ID\n' >&2; exit 1; }

request accounts -X GET "$BASE_URL/api/v1/accounts" \
    -H "Authorization: Bearer $TOKEN"
require_status accounts '^2[0-9][0-9]$'
ACCOUNT_ID="$(jq -r --arg broker "$BROKER_ACCOUNT_ID" '.[] | select(.brokerAccountId == $broker) | .accountId' "$TMP_DIR/accounts.body")"
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "null" ]]; then
    printf 'PAPER financial account was not created for broker account %s\n' "$BROKER_ACCOUNT_ID" >&2
    exit 1
fi
request account_detail -X GET "$BASE_URL/api/v1/accounts/$ACCOUNT_ID" \
    -H "Authorization: Bearer $TOKEN"
require_status account_detail '^2[0-9][0-9]$'
ACCOUNT_BALANCE="$(jq -r '.balances.balances.USD // empty' "$TMP_DIR/account_detail.body")"
ACCOUNT_CURRENCY="$(jq -r '.baseCurrency // empty' "$TMP_DIR/account_detail.body")"

request scope -X POST "$BASE_URL/api/v1/intelligence/scans/scope" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    --data "{\"accountId\":\"$ACCOUNT_ID\",\"objective\":\"runtime PAPER risk proof\",\"requestedMarketIds\":null,\"scopeMode\":\"ALL_ELIGIBLE\"}"
require_status scope '^2[0-9][0-9]$'

SCAN_KEY="runtime-scan-$USERNAME"
request scan -X POST "$BASE_URL/api/v1/intelligence/scans" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $SCAN_KEY" \
    --data "{\"accountId\":\"$ACCOUNT_ID\",\"objective\":\"runtime PAPER risk proof\",\"requestedMarketIds\":null,\"scopeMode\":\"ALL_ELIGIBLE\"}"
require_status scan '^2[0-9][0-9]$'
SCAN_ID="$(jq -r '.scanId' "$TMP_DIR/scan.body")"
[[ -n "$SCAN_ID" && "$SCAN_ID" != "null" ]] || { printf 'Scan creation returned no ID\n' >&2; exit 1; }

for attempt in $(seq 1 30); do
    request scan_state -X GET "$BASE_URL/api/v1/intelligence/scans/$SCAN_ID" \
        -H "Authorization: Bearer $TOKEN"
    SCAN_STATE="$(jq -r '.status // .state // .phase // empty' "$TMP_DIR/scan_state.body")"
    case "$SCAN_STATE" in
        COMPLETED|SUCCEEDED|FAILED|REJECTED|CANCELLED) break ;;
    esac
    sleep 2
done
if [[ "$SCAN_STATE" != "COMPLETED" && "$SCAN_STATE" != "SUCCEEDED" ]]; then
    printf 'Scan did not complete successfully: %s\n' "$(body_of scan_state)" >&2
    exit 1
fi

request opportunities -X GET "$BASE_URL/api/v1/opportunities/active" \
    -H "Authorization: Bearer $TOKEN"
require_status opportunities '^2[0-9][0-9]$'
if [[ "$STRICT_PAPER_ENTRY" == "true" ]]; then
    OPPORTUNITY_ID="$(jq -r 'map(select((.direction // .tradeDirection // "") == "LONG" and ((.instrument // "") | endswith("/USD"))))[0].id // empty' "$TMP_DIR/opportunities.body")"
elif [[ "$ALLOW_PAPER_SHORT" == "true" ]]; then
    OPPORTUNITY_ID="$(jq -r 'map(select((.direction // .tradeDirection // "") == "SHORT" and ((.instrument // "") | endswith("/USD"))))[0].id // empty' "$TMP_DIR/opportunities.body")"
else
    OPPORTUNITY_ID="$(jq -r 'map(select((.direction // .tradeDirection // "") == "LONG" and ((.instrument // "") | endswith("/USD")))) | .[0].id // .[0].opportunityId // empty' "$TMP_DIR/opportunities.body")"
fi
if [[ -z "$OPPORTUNITY_ID" || "$OPPORTUNITY_ID" == "null" ]]; then
    printf 'No compatible PAPER opportunity was produced by scan %s. Candidates: %s\n' "$SCAN_ID" \
        "$(jq -c 'map({id, status, direction, tradeDirection, instrument, marketId})' "$TMP_DIR/opportunities.body")" >&2
    exit 1
fi
OPPORTUNITY_JSON="$(jq -c --arg id "$OPPORTUNITY_ID" 'map(select(.id == $id or .opportunityId == $id))[0]' "$TMP_DIR/opportunities.body")"
MARKET_ID="$(jq -r '.marketId // empty' <<< "$OPPORTUNITY_JSON")"
MARKET_SYMBOL="$(jq -r '.instrument // empty' <<< "$OPPORTUNITY_JSON")"
REFERENCE_PRICE="$(jq -r '.setup.referencePrice // empty' <<< "$OPPORTUNITY_JSON")"
REFERENCE_PRICE_AT="$(jq -r '.setup.referencePriceAt // empty' <<< "$OPPORTUNITY_JSON")"
if [[ -z "$MARKET_ID" || "$MARKET_ID" == "null" ]]; then
    request markets -X GET "$BASE_URL/api/v1/markets" \
        -H "Authorization: Bearer $TOKEN"
    require_status markets '^2[0-9][0-9]$'
    MARKET_ID="$(jq -r --arg symbol "$MARKET_SYMBOL" 'map(select(.symbol == $symbol))[0].marketId // empty' "$TMP_DIR/markets.body")"
fi
if [[ -n "$MARKET_ID" && "$MARKET_ID" != "null" ]]; then
    request market -X GET "$BASE_URL/api/v1/markets/$MARKET_ID" \
        -H "Authorization: Bearer $TOKEN"
    require_status market '^2[0-9][0-9]$'
    MARKET_VALUATION_AT="$(jq -r '.marketState.lastUpdated // empty' "$TMP_DIR/market.body")"
    request ohlc -X GET "$BASE_URL/api/v1/markets/$MARKET_ID/ohlc?interval=ONE_MINUTE&limit=1" \
        -H "Authorization: Bearer $TOKEN"
    require_status ohlc '^2[0-9][0-9]$'
    OHLC_PRICE="$(jq -r '.[-1].close // empty' "$TMP_DIR/ohlc.body")"
    OHLC_AT="$(jq -r '.[-1].occurredAt // empty' "$TMP_DIR/ohlc.body")"
    OHLC_EPOCH="$(date -u -d "$OHLC_AT" '+%s' 2>/dev/null || true)"
    REFERENCE_EPOCH="$(date -u -d "$REFERENCE_PRICE_AT" '+%s' 2>/dev/null || true)"
    if [[ -n "$OHLC_PRICE" && -n "$OHLC_EPOCH" && ( -z "$REFERENCE_EPOCH" || "$OHLC_EPOCH" -gt "$REFERENCE_EPOCH" ) ]]; then
        REFERENCE_PRICE="$OHLC_PRICE"
        REFERENCE_PRICE_AT="$OHLC_AT"
    fi
else
    MARKET_VALUATION_AT=""
fi
if [[ -z "$MARKET_ID" || "$MARKET_ID" == "null" ]]; then
    printf 'Selected opportunity did not resolve to a market ID: %s\n' "$OPPORTUNITY_JSON" >&2
    exit 1
fi
if [[ -z "$MARKET_VALUATION_AT" || "$MARKET_VALUATION_AT" == "null" ]]; then
    MARKET_VALUATION_AT="$REFERENCE_PRICE_AT"
fi
PROOF_STARTED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
PROOF_EPOCH="$(date -u -d "$PROOF_STARTED_AT" '+%s')"
MARKET_EPOCH="$(date -u -d "$MARKET_VALUATION_AT" '+%s' 2>/dev/null || true)"
REFERENCE_EPOCH="$(date -u -d "$REFERENCE_PRICE_AT" '+%s' 2>/dev/null || true)"
if [[ -n "$REFERENCE_EPOCH" && "$REFERENCE_EPOCH" -le "$PROOF_EPOCH" && ( -z "$MARKET_EPOCH" || "$REFERENCE_EPOCH" -gt "$MARKET_EPOCH" ) ]]; then
    MARKET_VALUATION_AT="$REFERENCE_PRICE_AT"
    MARKET_EPOCH="$REFERENCE_EPOCH"
fi
VALUATION_EPOCH="$MARKET_EPOCH"
if [[ -z "$VALUATION_EPOCH" || "$VALUATION_EPOCH" -gt "$PROOF_EPOCH" || $((PROOF_EPOCH - VALUATION_EPOCH)) -gt 900 ]]; then
    printf 'Market valuation timestamp is missing, future-dated, or older than 15 minutes: %s\n' \
        "$MARKET_VALUATION_AT" >&2
    exit 1
fi

PLAN_KEY="runtime-plan-$USERNAME"
request plan -X POST "$BASE_URL/api/v1/trade-plans/opportunities/$OPPORTUNITY_ID/trade-plans" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $PLAN_KEY" \
    --data "{\"accountId\":\"$ACCOUNT_ID\"}"
require_status plan '^2[0-9][0-9]$'
TRADE_PLAN_ID="$(jq -r '.tradePlanId // empty' "$TMP_DIR/plan.body")"
TRADE_PLAN_VERSION="$(jq -r '.tradePlanVersion // 1' "$TMP_DIR/plan.body")"
[[ -n "$TRADE_PLAN_ID" && "$TRADE_PLAN_ID" != "null" ]] || { printf 'Trade plan creation returned no ID\n' >&2; exit 1; }
request plan_detail_initial -X GET "$BASE_URL/api/v1/trade-plans/$TRADE_PLAN_ID/versions/$TRADE_PLAN_VERSION" \
    -H "Authorization: Bearer $TOKEN"
require_status plan_detail_initial '^2[0-9][0-9]$'

request decision -X POST "$BASE_URL/api/v1/trade-plans/$TRADE_PLAN_ID/versions/$TRADE_PLAN_VERSION/decisions" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    --data '{"decision":"ACCEPT"}'
require_status decision '^2[0-9][0-9]$'

TRADE_PLAN_VERSION="$(jq -r '.version // .tradePlanVersion // .versionNumber // empty' "$TMP_DIR/decision.body")"
DECISION_STATE="$(jq -r '.status // empty' "$TMP_DIR/decision.body")"
if [[ -z "$TRADE_PLAN_VERSION" || "$TRADE_PLAN_VERSION" == "null" || "$TRADE_PLAN_VERSION" == "0" ]]; then
    printf 'Decision response did not return a plan version: %s\n' "$(body_of decision)" >&2
    exit 1
fi
if [[ "$DECISION_STATE" != "ACCEPTED" && "$DECISION_STATE" != "READY" && "$DECISION_STATE" != "READY_TO_EXECUTE" ]]; then
    printf 'Decision response did not return an executable plan state: %s\n' "$(body_of decision)" >&2
    exit 1
fi
request plan_detail -X GET "$BASE_URL/api/v1/trade-plans/$TRADE_PLAN_ID/versions/$TRADE_PLAN_VERSION" \
    -H "Authorization: Bearer $TOKEN"
require_status plan_detail '^2[0-9][0-9]$'
GENERATED_QUANTITY="$(jq -r '.quantity // empty' "$TMP_DIR/plan_detail.body")"
GENERATED_ENTRY_PRICE="$(jq -r '.entryPrice // empty' "$TMP_DIR/plan_detail.body")"
GENERATED_NOTIONAL="$(jq -r '.notional // empty' "$TMP_DIR/plan_detail.body")"
GENERATED_MONETARY_RISK="$(jq -r '.monetaryRisk // empty' "$TMP_DIR/plan_detail.body")"

RISK_KEY="runtime-risk-$USERNAME"
request risk -X POST "$BASE_URL/api/v1/trade-plans/$TRADE_PLAN_ID/versions/$TRADE_PLAN_VERSION/risk-evaluations" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $RISK_KEY" \
    --data "{\"accountId\":\"$ACCOUNT_ID\"}"
require_status risk '^2[0-9][0-9]$'
EVALUATION_ID="$(jq -r '.evaluationId // empty' "$TMP_DIR/risk.body")"
APPROVED="$(jq -r '.approved // false' "$TMP_DIR/risk.body")"
[[ -n "$EVALUATION_ID" && "$EVALUATION_ID" != "null" ]] || { printf 'Risk evaluation returned no evaluation ID\n' >&2; exit 1; }

RISK_RESPONSE="$(jq -c . "$TMP_DIR/risk.body")"
EXPECTED_NOTIONAL="$(jq -n --arg quantity "$GENERATED_QUANTITY" --arg price "$GENERATED_ENTRY_PRICE" '($quantity | tonumber) * ($price | tonumber)')"
NOTIONAL_DELTA="$(jq -n --arg actual "$GENERATED_NOTIONAL" --arg expected "$EXPECTED_NOTIONAL" '((($actual | tonumber) - ($expected | tonumber)) | fabs)')"
SIZING_CONSISTENT="$(jq -n --arg delta "$NOTIONAL_DELTA" '($delta | tonumber) <= 0.000001')"
if [[ "$SIZING_CONSISTENT" != "true" ]]; then
    printf 'Generated sizing is inconsistent: quantity=%s price=%s notional=%s expected=%s\n' \
        "$GENERATED_QUANTITY" "$GENERATED_ENTRY_PRICE" "$GENERATED_NOTIONAL" "$EXPECTED_NOTIONAL" >&2
    exit 1
fi

NEGATIVE_PLAN_ID=""
NEGATIVE_PLAN_VERSION=""
NEGATIVE_RISK_RESPONSE='{}'
NEGATIVE_PLAN_STATUS="not_attempted"
if [[ -n "$MARKET_ID" && "$MARKET_ID" != "null" && -n "$REFERENCE_PRICE" && "$REFERENCE_PRICE" != "null" ]]; then
    NEGATIVE_PAYLOAD="$(jq -n \
        --arg accountId "$ACCOUNT_ID" \
        --arg marketId "$MARKET_ID" \
        --arg referencePrice "$REFERENCE_PRICE" \
        '{accountId: $accountId, marketId: $marketId, direction: "LONG", entryType: "MARKET", referencePrice: ($referencePrice | tonumber), quantity: 100000, monetaryRisk: 1000, thesis: "Story 0055 negative excessive exposure validation", confirmationConditions: ["runtime validation"], invalidationConditions: ["risk rejection"], managementRules: []}' )"
    request negative_plan -X POST "$BASE_URL/api/v1/trade-plans/manual" \
        -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' \
        -H "Idempotency-Key: runtime-negative-$USERNAME" \
        --data "$NEGATIVE_PAYLOAD"
    NEGATIVE_PLAN_STATUS="$(status_of negative_plan)"
    NEGATIVE_PLAN_ID="$(jq -r '.tradePlanId // empty' "$TMP_DIR/negative_plan.body")"
    NEGATIVE_PLAN_VERSION="$(jq -r '.tradePlanVersion // 1' "$TMP_DIR/negative_plan.body")"
    if [[ -n "$NEGATIVE_PLAN_ID" ]]; then
        request negative_decision -X POST "$BASE_URL/api/v1/trade-plans/$NEGATIVE_PLAN_ID/versions/$NEGATIVE_PLAN_VERSION/decisions" \
            -H "Authorization: Bearer $TOKEN" \
            -H 'Content-Type: application/json' \
            --data '{"decision":"ACCEPT"}'
        NEGATIVE_PLAN_VERSION="$(jq -r '.version // .tradePlanVersion // 2' "$TMP_DIR/negative_decision.body")"
        request negative_risk -X POST "$BASE_URL/api/v1/trade-plans/$NEGATIVE_PLAN_ID/versions/$NEGATIVE_PLAN_VERSION/risk-evaluations" \
            -H "Authorization: Bearer $TOKEN" \
            -H 'Content-Type: application/json' \
            -H "Idempotency-Key: runtime-negative-risk-$USERNAME" \
            --data "{\"accountId\":\"$ACCOUNT_ID\"}"
        NEGATIVE_RISK_RESPONSE="$(jq -c . "$TMP_DIR/negative_risk.body")"
    fi
fi

EXECUTION_ID=""
EXECUTION_VALIDATE_STATUS="not_attempted"
EXECUTION_STATUS="not_attempted"
EXECUTION_RESPONSE='{}'
EXECUTION_SUBMIT_STATUS="not_attempted"
EXECUTION_SUBMIT_RESPONSE='{}'
POSITIONS_STATUS="not_attempted"
POSITIONS_RESPONSE='[]'
ACCOUNT_AFTER_EXECUTION='{}'
if [[ "$EXECUTE_PAPER" == "true" ]]; then
    if [[ "$APPROVED" != "true" ]]; then
        printf 'PAPER execution requested but risk was not approved: %s\n' "$RISK_RESPONSE" >&2
        exit 1
    fi
    EXPIRES_AT="$(date -u -d '+1 hour' '+%Y-%m-%dT%H:%M:%SZ')"
    request execution_validate -X POST "$BASE_URL/api/v1/executions/validate" \
        -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' \
        -H "Idempotency-Key: runtime-execution-$USERNAME" \
        --data "{\"tradePlanId\":\"$TRADE_PLAN_ID\",\"tradePlanVersion\":$TRADE_PLAN_VERSION,\"evaluationId\":\"$EVALUATION_ID\",\"brokerAccountId\":\"$BROKER_ACCOUNT_ID\",\"expiresAt\":\"$EXPIRES_AT\"}"
    EXECUTION_VALIDATE_STATUS="$(status_of execution_validate)"
    require_status execution_validate '^2[0-9][0-9]$'
    EXECUTION_ID="$(jq -r '.id // .executionId // empty' "$TMP_DIR/execution_validate.body")"
    if [[ -n "$EXECUTION_ID" ]]; then
        request execution -X POST "$BASE_URL/api/v1/executions/$EXECUTION_ID/execute" \
            -H "Authorization: Bearer $TOKEN"
        EXECUTION_SUBMIT_STATUS="$(status_of execution)"
        require_status execution '^2[0-9][0-9]$'
        EXECUTION_SUBMIT_RESPONSE="$(jq -c . "$TMP_DIR/execution.body" 2>/dev/null || printf '%s' '{}')"
        [[ -n "$EXECUTION_SUBMIT_RESPONSE" ]] || EXECUTION_SUBMIT_RESPONSE='{}'
        request execution_detail -X GET "$BASE_URL/api/v1/executions/$EXECUTION_ID" \
            -H "Authorization: Bearer $TOKEN"
        require_status execution_detail '^2[0-9][0-9]$'
        EXECUTION_STATUS="$(jq -r '.status // .state // .executionStatus // "UNKNOWN_RESPONSE"' "$TMP_DIR/execution_detail.body" 2>/dev/null || printf '%s' 'UNKNOWN_RESPONSE')"
        EXECUTION_RESPONSE="$(jq -c . "$TMP_DIR/execution_detail.body" 2>/dev/null || printf '%s' '{}')"
        if [[ "$EXECUTION_STATUS" != "COMPLETED" ]]; then
            printf 'PAPER execution did not complete: %s\n' "$EXECUTION_RESPONSE" >&2
            exit 1
        fi
        request positions -X GET "$BASE_URL/api/v1/accounts/$ACCOUNT_ID/positions" \
            -H "Authorization: Bearer $TOKEN"
        POSITIONS_STATUS="$(status_of positions)"
        require_status positions '^2[0-9][0-9]$'
        POSITIONS_RESPONSE="$(jq -c . "$TMP_DIR/positions.body" 2>/dev/null || printf '%s' '[]')"
        if [[ "$(jq 'length' <<< "$POSITIONS_RESPONSE")" -lt 1 ]]; then
            printf 'PAPER execution completed without a persisted position\n' >&2
            exit 1
        fi
        if [[ "$ALLOW_PAPER_SHORT" == "true" ]] && ! jq -e 'any(.[]; ((.side // .type // "") == "SELL") and ((.quantity // 0) > 0))' <<< "$POSITIONS_RESPONSE" >/dev/null; then
            printf 'PAPER short execution did not persist a SELL position: %s\n' "$POSITIONS_RESPONSE" >&2
            exit 1
        fi
        request account_after_execution -X GET "$BASE_URL/api/v1/accounts/$ACCOUNT_ID" \
            -H "Authorization: Bearer $TOKEN"
        require_status account_after_execution '^2[0-9][0-9]$'
        ACCOUNT_AFTER_EXECUTION="$(jq -c '{balances: .balances.balances, equity, baseCurrency}' "$TMP_DIR/account_after_execution.body" 2>/dev/null || printf '%s' '{}')"
    else
        printf 'PAPER execution validation did not return an execution ID: %s\n' "$(body_of execution_validate)" >&2
        exit 1
    fi
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"
jq -n \
    --arg generatedAt "$PROOF_STARTED_AT" \
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
    --arg expectedNotional "$EXPECTED_NOTIONAL" \
    --arg notionalDelta "$NOTIONAL_DELTA" \
    --arg sizingConsistent "$SIZING_CONSISTENT" \
    --arg accountBalance "$ACCOUNT_BALANCE" \
    --arg accountCurrency "$ACCOUNT_CURRENCY" \
    --argjson profileRules "$PROFILE_RULES" \
    --arg marketId "$MARKET_ID" \
    --arg valuationTimestamp "$MARKET_VALUATION_AT" \
    --arg referencePrice "$REFERENCE_PRICE" \
    --arg referencePriceAt "$REFERENCE_PRICE_AT" \
    --arg generatedEntryPrice "$GENERATED_ENTRY_PRICE" \
    --arg generatedQuantity "$GENERATED_QUANTITY" \
    --arg generatedNotional "$GENERATED_NOTIONAL" \
    --arg generatedMonetaryRisk "$GENERATED_MONETARY_RISK" \
    --arg negativePlanStatus "$NEGATIVE_PLAN_STATUS" \
    --arg negativePlanId "$NEGATIVE_PLAN_ID" \
    --arg negativePlanVersion "$NEGATIVE_PLAN_VERSION" \
    --arg negativeRiskResponse "$NEGATIVE_RISK_RESPONSE" \
    --arg executionId "$EXECUTION_ID" \
    --arg executionStatus "$EXECUTION_STATUS" \
    --arg executionResponse "$EXECUTION_RESPONSE" \
    --arg executionSubmitStatus "$EXECUTION_SUBMIT_STATUS" \
    --arg executionSubmitResponse "$EXECUTION_SUBMIT_RESPONSE" \
    --arg positionsStatus "$POSITIONS_STATUS" \
    --arg positionsResponse "$POSITIONS_RESPONSE" \
    --arg accountAfterExecution "$ACCOUNT_AFTER_EXECUTION" \
     '{generatedAt: $generatedAt, baseUrl: $baseUrl, username: $username, registration: {status: ($registerStatus | tonumber)}, profile: {id: $profileId, version: $profileVersion, rules: $profileRules}, paper: {brokerAccountId: $brokerAccountId, accountId: $accountId, balance: ($accountBalance | tonumber), currency: $accountCurrency}, scan: {status: ($scanStatus | tonumber), id: $scanId, state: $scanState, scopeStatus: ($scopeStatus | tonumber)}, opportunityId: $opportunityId, valuation: {marketId: $marketId, referencePrice: ($referencePrice | tonumber), opportunityReferencePriceAt: $referencePriceAt, observedAt: $valuationTimestamp}, tradePlan: {status: ($planStatus | tonumber), id: $tradePlanId, version: $tradePlanVersion, decisionStatus: ($decisionStatus | tonumber), entryPrice: ($generatedEntryPrice | tonumber), quantity: ($generatedQuantity | tonumber), notional: ($generatedNotional | tonumber), expectedNotional: ($expectedNotional | tonumber), notionalDelta: ($notionalDelta | tonumber), sizingConsistent: ($sizingConsistent == "true"), monetaryRisk: ($generatedMonetaryRisk | tonumber)}, risk: {status: ($riskStatus | tonumber), evaluationId: $evaluationId, approved: ($approved == "true"), response: ($riskResponse | fromjson)}, execution: {id: $executionId, submitStatus: (if ($executionSubmitStatus | test("^[0-9]+$")) then ($executionSubmitStatus | tonumber) else $executionSubmitStatus end), submitResponse: ($executionSubmitResponse | fromjson), status: $executionStatus, response: ($executionResponse | fromjson)}, positions: {status: (if ($positionsStatus | test("^[0-9]+$")) then ($positionsStatus | tonumber) else $positionsStatus end), response: ($positionsResponse | fromjson)}, negativeValidation: {planStatus: (if ($negativePlanStatus | test("^[0-9]+$")) then ($negativePlanStatus | tonumber) else $negativePlanStatus end), planId: $negativePlanId, planVersion: (if ($negativePlanVersion | test("^[0-9]+$")) then ($negativePlanVersion | tonumber) else $negativePlanVersion end), risk: ($negativeRiskResponse | fromjson)}}' \
    > "$OUTPUT_FILE"

jq . "$OUTPUT_FILE"
