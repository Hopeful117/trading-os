# Implementation Report - Story 0116

## Outcome

Workflow approvals recorded for this implementation:

- Story 0116 approved by the human engineer;
- Repository Analysis approved by the human engineer;
- Implementation Plan approved by the human engineer.

Implemented the first bounded Trading Core historical economic lifecycle:

```text
StrategyIntent
    -> entry historical MARKET replay
    -> actual filled exposure
    -> exit historical MARKET replay
    -> explicit TransactionCosts
    -> EconomicResult
```

The implementation preserves execution truth independently from cost
completeness. Missing historical commission and funding remain explicit and do
not become zero.

## Implementation

### New Types

- `HistoricalEconomicEvaluationRequest`
  - explicit market, entry intent, entry cutoff, exit cutoff, research cutoff,
    transaction costs, and policy versions;
- `HistoricalEconomicEvaluationService`
  - bounded application orchestration for entry, actual exposure, exit, and
    lifecycle evaluation.

### Modified Types

- `HistoricalMarketExecutionService`
  - added a replay result carrying both the historical `ReferenceMarketState`
    and `SimulatedExecution` while preserving the existing `execute` method;
- `EconomicLifecycleEvaluator`
  - supports an explicit research cutoff for cost evidence eligibility;
  - preserves lifecycle results for unavailable entry/exit execution;
  - calculates and fingerprints explicit closed/residual quantities;
- `EconomicResult`
  - now exposes `closedQuantity` and `residualExposure`;
  - preserves the existing constructor and single-leg compatibility path.

### Existing Types Reused

- `StrategyIntent`;
- `HistoricalMarketExecutionService` and Story 0115 replay semantics;
- `SimulatedExecution` and visible-depth fills/VWAP;
- `ReferenceMarketState`;
- `TransactionCosts`, `ExplicitCost`, and `CostEvidence`;
- `EconomicLifecycleEvaluator`;
- `EconomicResult` and canonical result/completeness statuses.

No parallel strategy, execution, or economic domain was introduced.

## Lifecycle Behavior

- Entry uses the supplied canonical intent and entry cutoff.
- Exit uses a fresh opposite-side MARKET intent at the explicit exit cutoff.
- Exit quantity is the actual entry `filledQuantity`.
- Partial entry does not create exposure for the unfilled remainder.
- Partial exit preserves `closedQuantity` and `residualExposure`.
- Zero entry does not trigger an exit replay.
- Missing exit replay preserves residual exposure and does not fabricate P&L.
- No OHLC, current ticker, current Order Book, PAPER, broker, or Risk fallback is
  used.

## Economic Semantics

- Reference market return remains separate from strategy P&L.
- Execution-adjusted P&L uses entry/exit execution prices and closed quantity.
- Visible-depth spread/degradation is not charged a second time.
- Fully costed closed lifecycles may produce authoritative NetPnL.
- Incomplete perpetual cost evidence produces a degraded result with available
  execution-adjusted P&L but no authoritative NetPnL.
- `NetReturn` remains unavailable because no canonical denominator policy exists.
- Available zero commission is distinct from unavailable commission.
- Spot funding can be `NOT_APPLICABLE`; perpetual funding without evidence stays
  `UNAVAILABLE`.

## Temporal and Identity Semantics

- Entry and exit replay cutoffs are independent.
- Available cost evidence must be known by the explicit research cutoff.
- Existing effective-interval and execution-time eligibility checks remain in
  the lifecycle evaluator.
- Economic fingerprints include execution fingerprints, cost status/evidence,
  policy versions, result completeness/status, and research cutoff.
- Repeated equal lifecycle inputs produce equal economic fingerprints.

## Documentation Reconciliation

Documentation update: this Story implementation report was added as the
canonical implementation record. No ADR, public API, dataset, runtime
configuration, PAPER, Risk, Market Data, or Broker documentation required
updating because those boundaries were not changed.

Vault Outcome: no vault consultation was performed for implementation; no vault
action is proposed. The repository ADRs and current source were sufficient and
remain authoritative for this change.

## Validation

### Passed

```text
cd trading-core && ./mvnw -q -Dtest=HistoricalEconomicEvaluationServiceTest,EconomicLifecycleEvaluatorTest,HistoricalMarketExecutionServiceTest,VisibleDepthMarketExecutionCalculatorTest,MarketOrderEconomicEvaluatorTest test
cd market-data && ./mvnw -q -Dtest=OrderBookReplayServiceTest test
cd trading-core && ./mvnw -q -DskipTests package
git diff --check
```

Covered scenarios include:

- positive execution-adjusted perpetual result with unavailable costs;
- fully costed spot reference;
- LONG and SHORT direction;
- partial entry and partial exit residual exposure;
- zero entry and unavailable exit replay;
- future cost evidence;
- zero commission versus unavailable commission;
- deterministic repeated fingerprints;
- existing replay and economic regression tests.

### Blocked or Limited

- Full Trading Core integration validation was attempted. The environment
  reported PostgreSQL/Testcontainers connection refusals and existing scheduled
  integration-task errors. It is not reported as a clean full verification.
- Authenticated real historical economic runtime proof was not completed. The
  internal replay path requires service JWT access and no safe authenticated
  entry/exit proof was available during this run.
- SonarQube validation was not run because no configured project/quality command
  was established for this module during the implementation.

## Frozen Boundaries

```text
ObservedMarketOutcome changed = NO
research-dataset-v1 changed   = NO
feature-projection-v1 changed = NO
PAPER behavior changed        = NO
Risk behavior changed         = NO
Funding acquisition changed   = NO
Fee acquisition changed       = NO
Public API added              = NO
Persistence added             = NO
```

## Next Primary Story

**Economic Research Dataset**

Objective: define a separate versioned economic research sample contract that
consumes the now-proven `EconomicResult` semantics while preserving execution
provenance, cost completeness, residual exposure, and explicit unavailable
NetPnL. This is the next direct step toward chronological Java evaluation and
downstream Python/Jupyter consumption without modifying `research-dataset-v1`.

## Final Decision

The bounded economic lifecycle integration is implemented and focused
validation passes. Full integration validation and authenticated runtime proof
remain environment-limited and must be resolved before claiming complete
operational readiness or fully costed historical profitability.
