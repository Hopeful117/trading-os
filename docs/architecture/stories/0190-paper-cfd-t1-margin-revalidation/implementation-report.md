# Implementation Report - Story 0190

## Result

Implemented locally and left uncommitted for human review. PAPER T1 required
margin now consumes the independently acquired T1 valuation context and uses its
authoritative source price. `CASH + CFD` remains rejected.

## Changes

- Extended `RequiredMarginPort.Request` with valuation snapshot and freshness
  requirement context while preserving compatibility constructors.
- T0 and T1 orchestration now pass their already-acquired valuation snapshots.
- T1 PAPER margin request price is the fresh T1 valuation `sourcePrice`.
- PAPER margin resolution no longer calls Market Data or uses hardcoded T0
  freshness; missing context, freshness mismatch, stale facts, and missing price
  fail closed.
- PAPER calculation uses the authoritative valuation `sourcePrice` rather than
  `Request.price`.
- Added direct T0/T1 margin behavior, stale/missing-price, freshness mismatch,
  and application T1 request assertions.

## Non-Changes

```text
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
RISK_DOMAIN_CHANGED = NO
SETTLEMENT_CHANGED = NO
DATABASE_MIGRATION = NO
PAPER_PROVIDER_PREVIEW_USED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
PROVIDER_MUTATIONS = 0
```

## Validation

```text
FOCUSED_TESTS: PASS
TRADING_CORE_TEST: TIMEOUT - no terminal result within 300 seconds
TRADING_CORE_VERIFY: NOT_RUN - full test did not complete
RISK_DOMAIN_VERIFY: PASS
MIGRATION_TESTS: FAIL - existing V34 legacy-provider migration case
V34_FAILURE_CLASSIFICATION: PRE_EXISTING / unrelated to this Story
SETTLEMENT_COMPATIBILITY_TEST: PASS
BROKER_REQUIRED_MARGIN_CLIENT_TEST: PASS
GIT_DIFF_CHECK: PASS
NEW_HIGH_MEDIUM_STATIC_ISSUES: NOT_RUN
```

The focused command covered `PaperRequiredMarginClientTest`,
`ExecutionTimeRiskRevalidationServiceTest`, `TradePlanRiskEvaluationServiceTest`,
`RequiredMarginCurrencyNormalizerTest`, `CfdRiskProjectionServiceTest`,
`BrokerRequiredMarginClientTest`, and `SettlementCompatibilityPolicyTest`.

Migration output demonstrates fresh and historical paths reaching V49 before the
unrelated V34 legacy-provider case fails. Known Story 0189 issues concerning V49
persistence hardening and the missing Story 0188 code review remain outside this
Story's scope.

## Documentation Outcome

This Story's repository artifacts document the changed T1 authority boundary. No
ADR, V49 migration, or unrelated Story artifact was modified. The Obsidian vault
was not consulted and no vault action is proposed.
