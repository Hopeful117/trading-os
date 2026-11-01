# Implementation Report - Story 0189

## Result

**BLOCKED.** `CASH + CFD` remains rejected. No production source, migration,
settlement policy, or compatibility matrix was changed.

## Baseline

```text
REMOTE_MAIN: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
LOCAL_BASE: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
LOCAL_HEAD: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
BRANCH: story/0189-adr-082-phase-8-cash-cfd-compatibility-retry
STORY: 0189
```

## Story 0188 Baseline

```text
STORY_0188_PRESENT: YES
MIGRATION_V49_PRESENT: YES
ADR_083_ACCEPTED: YES
STORY_0188_CODE_REVIEW_PRESENT: NO
```

## Final Decision

```text
READY_FOR_CASH_PLUS_CFD: NO
SETTLEMENT_COMPATIBILITY_CHANGED: NO
ADR_082_PHASE_8: BLOCKED
SOFTWARE_CAPABILITY_READY: NO
LIVE_RUNTIME_VALIDATED: NO
```

## Validation

```text
FOCUSED_TESTS: PASS
TRADING_CORE_TEST: NOT_TERMINALLY_VALIDATED - exceeded 300 second tool bound
TRADING_CORE_VERIFY: NOT_RUN - chained command stopped during test timeout
RISK_DOMAIN_VERIFY: PASS
BROKER_SERVICE_TEST: NOT_TOUCHED
MIGRATION_TESTS: FAIL - legacy provider migration test failed at V34
GIT_DIFF_CHECK: PASS
NEW_HIGH_MEDIUM_STATIC_ISSUES: NOT_RUN
```

The focused command passed:

```text
./mvnw -q -Dtest=PaperMarginCalculatorTest,PaperMarginExposureSnapshotTest,
RequiredMarginCurrencyNormalizerTest,BrokerRequiredMarginClientTest,
TradePlanRiskEvaluationServiceTest,ExecutionTimeRiskRevalidationServiceTest,
RiskPersistenceTest,SettlementCompatibilityPolicyTest test
```

The migration test reached V49 on fresh and historical upgrade paths before a
legacy provider migration case failed at V34. This is validation evidence, not
proof of V49 policy assignment correctness.

## Safety

```text
HUMAN_AUTHORIZATION_REQUIRED: YES
CTRADER_SAFETY_EXECUTION_ENABLED: false
REAL_CTRADER_ORDERS: 0
REAL_CTRADER_CLOSES: 0
PROVIDER_MUTATIONS: 0
```

Documentation outcome: this Story adds only repository Story artifacts. No ADR
or production documentation was changed. The vault was not consulted and no
vault action is proposed.
