# CFD Validation - G3 Risk & T0/T1 Corrective Run

## Run Identity

```text
RUN_ID = 2026-11-01-g3-risk-revalidation
DATE = 2026-11-01
REPOSITORY_SHA = edb5e7e47eee79d4cbe7491b2b5321b151c029df
REMOTE_MAIN = edb5e7e47eee79d4cbe7491b2b5321b151c029df
BRANCH = validation/cfd-epic-qualification
PRODUCTION_CODE_CHANGED = NO
SETTLEMENT_COMPATIBILITY_CHANGED = NO
```

The initial qualification report remains unchanged. This corrective run adds
only active G3 integration evidence. The DevLog context request timed out twice;
the current repository protocol, ADRs, production code and tests were used as
authoritative evidence.

## G1/G2 Regression

```text
G1_CONTRACT_PRODUCT = PASS
G2_MARGIN_AUTHORITY = PASS
```

Focused contract, CFD calculator, risk projection, valuation, margin, migration
and PostgreSQL policy tests passed. No G1/G2 production architecture changed.

## T0

```text
REAL_T0 = YES
T0_FRESHNESS_REQUIREMENT = FreshnessRequirement.t0() / PRE_TRADE / 5 minutes
T0_SOURCE_PRICE = 1.10000 USD per EUR
T0_REQUIRED_MARGIN = 11.00 USD
T0_DECISION = PASS
```

T0 executes through `TradePlanRiskEvaluationService`, the real PAPER margin
authority, `CfdRiskProjectionService`, persistence and the existing human
authorization operation. No Risk decision is injected by the test.

## T1 Positive

```text
REAL_T1 = YES
T1_MOCKED = NO
T1_FRESHNESS_REQUIREMENT = FreshnessRequirement.t1() / EXECUTION_TIME / 30 seconds
T1_SOURCE_PRICE = 1.10200 USD per EUR
T1_SOURCE_PRICE_DIFFERENT_FROM_T0 = YES
T1_USED_FACT_SOURCE_PRICE = YES
T1_REQUIRED_MARGIN = 11.02 USD through PaperRequiredMarginClient and the assigned 1% MarginPolicy
T1_DECISION = PASS
T0_AUTHORITY_REUSED = NO
```

The positive T0/T1 scenario is `PaperCfdDeterministicLifecycleIntegrationTest`
`provesT0AndT1WhileKeepingCfdSettlementBlocked`. It uses a controlled
authoritative valuation source through the existing Market Valuation port while
the production T0/T1 services and PAPER margin resolution execute. The T1
request uses `Fact.sourcePrice`, not the execution request price or T0 price.

## Missing T1

Existing active evidence remains:

```text
MISSING_FACT_TEST = PASS
FAIL_CLOSED = YES
EXECUTION_ALLOWED = NO
```

`ExecutionTimeRiskRevalidationServiceTest#paperT1FailsClosedWhenMarketValueHasNoSourcePrice`
proves the production T1 path returns unavailable before execution eligibility.

## Stale T1

```text
STALE_FACT_TEST = PASS
FACT_TIMESTAMP = 2026-11-01T11:59:29Z
DECISION_TIMESTAMP = 2026-11-01T12:00:00Z
ACTUAL_T1_FRESHNESS_LIMIT = PT30S
WHY_STALE = source effective/captured timestamps are 31 seconds older than the T1 decision boundary
FAIL_CLOSED = YES
EXECUTION_ALLOWED = NO
```

Evidence:

- `PaperCfdDeterministicLifecycleIntegrationTest#t1RejectsAStaleAuthoritativeValuationFact`
- Result: T1 reason `STALE_FACT`.
- No execution attempt, broker order, trade or account mutation was persisted.

## Freshness Mismatch

```text
FRESHNESS_MISMATCH_TEST = PASS
MISMATCH_SEMANTIC = valuation fact timestamps are recent, but the returned valuation snapshot declares PT5M while T1 requires PT30S
DISTINCT_FROM_STALE_CASE = YES
FACT_WALL_CLOCK_FRESH = YES
WHY_INVALID_FOR_T1 = PaperRequiredMarginClient rejects a valuation whose declared maxObservationAge does not equal the T1 requirement
FAIL_CLOSED = YES
EXECUTION_ALLOWED = NO
```

Evidence:

- `PaperCfdDeterministicLifecycleIntegrationTest#t1RejectsARecentFactWithAConflictingFreshnessContract`
- Fact timestamps are one second old, so this is not the stale-timestamp case.
- Result: T1 reason `MARGIN_FRESHNESS_MISMATCH`.
- No execution attempt, broker order, trade or account mutation was persisted.

## T0 PASS -> T1 REJECT

```text
T0_DECISION = PASS
HUMAN_AUTHORIZATION = YES
AUTHORITATIVE_CHANGE = T1 source price changes from 1.10000 to 100.00000
T0_REQUIRED_MARGIN = 11.00 USD
T1_REQUIRED_MARGIN = resolved through the real PaperRequiredMarginClient from the T1 valuation and local policy
T1_DECISION = REJECT
T0_PASS_T1_REJECT_PROVEN = YES
EXECUTION_AFTER_REJECT = NO
FILL_AFTER_REJECT = NO
SETTLEMENT_AFTER_REJECT = NO
FINANCIAL_MUTATION_AFTER_REJECT = NO
```

Evidence:

- `PaperCfdDeterministicLifecycleIntegrationTest#t1RejectionStopsBeforePaperSubmissionOrSettlement`
- `ExecutionTimeRiskRevalidationServiceTest#completePaperT1RevalidationApprovesWithZeroBalancesIgnored`
- The production T1 calculation consumes the current authoritative valuation
  and resolves required margin through the PAPER policy port. The rejected path
  persists the T1 decision and creates no execution attempt or broker order.

## Production Components

```text
T0 = TradePlanRiskEvaluationService
T1 = ExecutionTimeRiskRevalidationService
CFD projection = CfdRiskProjectionService
valuation contract = market_data.valuation.MarketValuationPort
valuation adapter = MarketValuationClient
PAPER margin = PaperRequiredMarginClient + MarginPolicyPersistence + PaperMarginCalculator
human authorization = ValidateAndCreateService / existing ExecutionIntent risk approval boundary
execution eligibility = T1 lifecycle transition to VALIDATED or RISK_REVALIDATION_REJECTED/UNAVAILABLE
```

Risk Domain remains provider-neutral. No cTrader, FTMO, Kraken,
`SettlementCompatibilityPolicy`, or settlement service concepts were added to
Risk logic.

## Architecture and Safety

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
RISK_DOMAIN_PROVIDER_NEUTRAL = YES
SETTLEMENT_COMPATIBILITY_CHANGED = NO
CASH_CFD = REJECTED
```

```text
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
AUTOMATIC_EXECUTION = NO
```

## Validation

| Check | Result | Evidence |
|---|---|---|
| Focused G3 integration | PASS | `PaperCfdDeterministicLifecycleIntegrationTest`: 5 tests, 0 failures, 0 skipped. |
| Focused G3/G1/G2 regression | PASS | Selected CFD, risk, margin, migration and compatibility tests exit 0. |
| `trading-core ./mvnw -q test` | PASS | Exit status 0. |
| `trading-core ./mvnw -q verify` | PASS | Exit status 0. |
| `risk-domain mvn -q verify` | PASS | Exit status 0. |
| `git diff --check` | PASS | No whitespace errors. |
| Repository quality pipeline | FAIL | Existing aggregate coverage gate: `broker-service` 75.33% versus required 80%. |
| Sonar quality scan | NOT_RUN | Quality script stopped at the coverage gate before scanner execution. |

The broad test and verify logs retain the known historical Flyway V34
failure-path log; Maven exit status was 0 and no migration was changed.

## Review

Focused review found no HIGH or MEDIUM G3 findings:

- T1 decision is not mocked.
- Stale and freshness-mismatch cases are distinct.
- No wall-clock sleep or timing race is used.
- Controlled facts enter through the existing Market Valuation port.
- T1 rejection prevents execution side effects.
- No compatibility policy, positive settlement suite, G4-G8 code or provider
  behavior was changed.

## Gate Result

```text
G3_RISK_T0_T1 = PASS
G3_BLOCKER = NONE
```

## CFD Epic Status

```text
G1 = PASS
G2 = PASS
G3 = PASS
G4 = BLOCKED
G5 = BLOCKED
G6 = BLOCKED
G7 = BLOCKED
G8 = BLOCKED

CFD_IMPLEMENTED = YES
CFD_PROVEN = NO (complete capability remains unproven)
CFD_VALIDATED = NO
READY_TO_ANALYZE_G4_G6_PRE_AUTH = YES
```

## Next Required Action

Stop this corrective run. Do not proceed automatically to G4, G5, G6 or G7.
The next validation run must analyze the pre-authorization evidence boundary
needed to prove positive accounting and replay while preserving
`CASH + CFD = REJECTED`.
