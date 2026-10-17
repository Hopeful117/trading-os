# Implementation Plan - Story 0116

## Status

Approved by the human engineer.

This plan implements the human-approved Repository Analysis for Story 0116
within `ADR-063` and `ADR-064`. The human engineer approved this plan before
implementation began.

---

## Implementation Boundary

Production changes remain bounded to Trading Core:

- a historical economic lifecycle application service and request boundary;
- the existing economic lifecycle domain primitive;
- the existing canonical `EconomicResult` contract only where required to make
  closed and residual quantities explicit;
- focused Trading Core tests and regression coverage;
- this Story's canonical documentation artifacts.

No Market Data production change is planned. The existing historical replay
contract remains authoritative. No Broker Service, Market Intelligence, PAPER,
Risk, dataset, public API, persistence, fee acquisition, or funding acquisition
change is planned.

---

## Fixed Design Decisions

### 1. Application Request

Introduce a small provider-neutral request record in the Trading Core
application layer, conceptually:

```text
HistoricalEconomicEvaluationRequest(
    marketId,
    entryIntent,
    entryExecutionCutoff,
    exitExecutionCutoff,
    researchCutoff,
    transactionCosts,
    executionModelVersion,
    transactionCostModelVersion
)
```

The request uses the canonical `StrategyIntent` for entry. The exit cutoff is
explicit; no second horizon type is introduced. `researchCutoff` is explicit so
cost evidence cannot be admitted merely because it was eligible at execution
time but known after the research boundary.

The request must validate:

- non-null market, intent, cutoffs, costs, and policy versions;
- entry cutoff equal to `entryIntent.decisionAt()` under the existing zero-latency
  MARKET policy;
- exit cutoff not before entry cutoff;
- research cutoff not before the relevant evaluation inputs;
- MARKET-only intent through the existing replay service.

### 2. Entry Orchestration

Call the existing `HistoricalMarketExecutionService` with:

```text
marketId + entryIntent + entryExecutionCutoff
```

Do not recreate book walking or inspect Market Data persistence in the new
service.

If the entry fills a positive quantity, use that exact `filledQuantity` as the
maximum exit quantity. Never use the requested entry quantity for exposure.

### 3. Exit Orchestration

Construct a new canonical `StrategyIntent` for the exit with:

- the same instrument;
- the opposite side;
- `MARKET` order type;
- quantity equal to the actual entry filled quantity;
- decision time equal to `exitExecutionCutoff` under the zero-latency policy.

Call `HistoricalMarketExecutionService` independently with the exit cutoff. The
entry market state/book must never be reused for the exit.

If entry fills zero, return an explicit execution-unavailable lifecycle result
without invoking an exit replay. If exit fills zero, preserve the entry
exposure and return an execution-unavailable/degraded lifecycle result without
synthetic closure.

### 4. Cost Boundary

The service receives canonical `TransactionCosts`; it does not acquire or
resolve fee/funding evidence. The caller/test fixture must use explicit states:

```text
perpetual without evidence:
    commission = UNAVAILABLE
    funding    = UNAVAILABLE

spot reference:
    commission = AVAILABLE
    funding    = NOT_APPLICABLE
```

The service must reject or classify stale available cost evidence when its
`knownAt` exceeds `researchCutoff`. Existing lifecycle eligibility checks remain
responsible for effective interval and execution-time eligibility.

The legacy numeric `TransactionCosts` constructor and
`MarketOrderEconomicEvaluator` are not used for this path.

### 5. Economic Calculation

Delegate entry/exit economics to `EconomicLifecycleEvaluator` after orchestration
has assembled both executions and states. Reuse its deterministic calculations:

```text
referenceMarketReturn
grossStrategyPnl
executionAdjustedPnl
```

Do not call the operational `TradingCalculatorService` from the historical
research path.

The resulting status remains separate from cost completeness:

- fully closed and fully costed: `AVAILABLE`, authoritative NetPnL allowed;
- executable but incomplete costs or partial lifecycle: `DEGRADED`, gross and
  execution-adjusted fields may remain available;
- no entry fill or no exit fill: `EXECUTION_UNAVAILABLE`, no synthetic P&L;
- stale/invalid inputs: existing `STALE` or `INVALID_INPUT` statuses.

`NetReturn` remains null because no canonical denominator policy exists.

### 6. Residual Exposure Contract

Extend `EconomicResult` minimally with explicit lifecycle quantities:

- `closedQuantity`;
- `residualExposure`.

Preserve existing record construction paths through compatible constructors or
factory methods and update all current tests/callers. For a fully closed
lifecycle, residual exposure is zero. For partial exit, it is:

```text
residualExposure = entry.filledQuantity - exit.filledQuantity
```

For no entry fill, both closed quantity and residual exposure are zero. For an
entry fill with no exit fill, residual exposure equals entry filled quantity.

No parallel economic result type is introduced.

### 7. Fingerprint

Retain the existing deterministic lifecycle fingerprint and ensure the semantic
material includes, directly or through execution/cost material:

- entry intent and side;
- entry and exit execution fingerprints;
- replay/evidence fingerprints;
- closed and residual quantities;
- cost component statuses/evidence fingerprints;
- execution and transaction-cost model versions;
- completeness and result status;
- explicit research cutoff when it affects admissibility.

No database IDs, request IDs, random UUIDs, wall-clock timestamps, or scheduler
values may enter the identity.

---

## Expected Files

### New

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/command/HistoricalEconomicEvaluationRequest.java`
  - immutable application input for entry/exit cutoffs, costs, and policy
    versions.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/HistoricalEconomicEvaluationService.java`
  - bounded orchestration of entry replay, actual exposure, exit replay, cost
    admissibility, and lifecycle evaluation.
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/application/service/HistoricalEconomicEvaluationServiceTest.java`
  - service-level orchestration and temporal boundary tests.

### Modified

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/EconomicResult.java`
  - expose closed quantity and residual exposure with compatibility preserved.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/EconomicLifecycleEvaluator.java`
  - populate/validate lifecycle quantities and include any new semantic fields
    in the result identity; preserve current calculation primitives.
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/economic/EconomicLifecycleEvaluatorTest.java`
  - extend existing lifecycle assertions for quantities, incomplete costs, and
    fingerprint semantics.

No Market Data, Broker Service, Market Intelligence, PAPER, Risk, or dataset
files should be modified unless implementation discovers a concrete contract
contradiction. Such a contradiction must stop implementation and be reported.

---

## Implementation Sequence

1. Confirm the approved Story and Repository Analysis remain unchanged.
2. Add the immutable application request and validate explicit cutoff/policy
   inputs.
3. Add the historical economic application service using the existing replay
   service for entry and exit.
4. Add explicit exposure quantities to `EconomicResult` with compatibility
   constructors/factories.
5. Update `EconomicLifecycleEvaluator` to populate the quantities and preserve
   deterministic identity without changing its P&L formulas.
6. Add service tests for full LONG/SHORT lifecycles, partial entry, partial
   exit, zero entry, zero exit, distinct entry/exit replays, and future cost
   evidence.
7. Extend domain tests for SPOT fully-costed semantics, perpetual unavailable
   funding/commission, zero commission versus unavailable, no double-counting,
   and fingerprint stability/sensitivity.
8. Run focused tests and inspect all affected call sites.
9. Run affected Trading Core validation and existing replay/PAPER regression
   tests.
10. Attempt authenticated runtime proof only if both historical cutoffs have
    admissible evidence; do not use anonymous access or current state fallback.
11. Reconcile Story documentation and produce the Implementation Report. Do not
    modify ADR-063 or ADR-064.

---

## Test Plan

### Service Orchestration

- full LONG: BUY entry and SELL exit;
- full SHORT: SELL entry and BUY exit;
- entry and exit use independent cutoffs and port calls;
- entry partial fill uses filled quantity for exit request;
- partial exit records closed and residual quantities;
- zero entry does not invoke exit replay;
- zero exit retains residual exposure and no P&L;
- stale/future cost evidence cannot upgrade completeness;
- replay failure remains execution unavailable and fail-closed.

### Economic Accounting

- reference return remains separate from strategy P&L;
- execution-adjusted P&L uses entry/exit VWAP and closed quantity;
- positive and negative gross execution-adjusted scenarios preserve unknown net;
- visible-depth execution prices are not charged a second spread/slippage cost;
- fully costed SPOT reference can produce authoritative NetPnL;
- perpetual unavailable commission/funding produces degraded/incomplete result;
- unavailable and not-applicable components remain distinct;
- NetReturn remains unavailable without a canonical denominator.

### Identity and Temporal Integrity

- identical lifecycle inputs produce identical result/fingerprint;
- changed entry/exit execution changes fingerprint;
- changed cost status/evidence changes fingerprint;
- changed policy version changes fingerprint;
- entry/exit `knownAt` and cost `knownAt` future leakage is rejected;
- residual/closed quantity changes are reflected in result identity.

### Regression

- existing `EconomicLifecycleEvaluatorTest`;
- existing `MarketOrderEconomicEvaluatorTest`;
- existing deterministic MARKET replay tests;
- existing PAPER and execution boundary regressions;
- `git diff --check`.

---

## Validation Commands

Initial focused validation:

```bash
cd trading-core && ./mvnw -q -Dtest=HistoricalEconomicEvaluationServiceTest,EconomicLifecycleEvaluatorTest test
```

Affected economic/replay validation:

```bash
cd trading-core && ./mvnw -q -Dtest=HistoricalEconomicEvaluationServiceTest,EconomicLifecycleEvaluatorTest,MarketOrderEconomicEvaluatorTest,HistoricalMarketExecutionServiceTest,VisibleDepthMarketExecutionCalculatorTest test
cd market-data && ./mvnw -q -Dtest=OrderBookReplayServiceTest test
```

Module validation:

```bash
cd trading-core && ./mvnw verify
```

If integration dependencies are unavailable, report the exact failure and keep
focused validation evidence separate from full verification.

---

## Runtime Proof

Use the existing authenticated internal replay path only if persisted evidence
supports two cutoffs for the same market/product. Use a small quantity and do
not mutate Market Data. Repeat the same request and compare EconomicResult
values, statuses, and fingerprint. Report positive execution-adjusted P&L as
pre-cost economics only when commission/funding remain unavailable.

---

## Documentation Reconciliation

Update only the Story 0116 implementation report if required by the workflow.
It must record:

- lifecycle service and reused primitives;
- actual exposure and residual semantics;
- cost completeness and missingness behavior;
- no NetPnL/NetReturn claim when incomplete;
- temporal and fingerprint validation;
- runtime proof status;
- `Documentation update: Not required` for unrelated architectural or dataset
  documentation if no other canonical document changed.

No ADR amendment, dataset migration, public API documentation, or runtime
configuration change is planned.

---

## Plan Decision

```text
ARCHITECTURE_BLOCKER = NONE
ADR_AMENDMENT_REQUIRED = NO
NEW_SERVICE = NO
PUBLIC_API = NO
PERSISTENCE = NO
MARKET_DATA_SEMANTIC_CHANGE = NO
BROKER_FEE_ACQUISITION = NO
FUNDING_ACQUISITION = NO
PAPER_OR_RISK_CHANGE = NO
IMPLEMENTATION_READY_AFTER_HUMAN_PLAN_APPROVAL = YES
```
