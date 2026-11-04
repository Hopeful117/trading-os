# Repository Analysis - Story 0195

## Scope

The requested change is limited to evidence-aware risk evaluation and its
provider-neutral contracts. No cTrader availability investigation is required.

## Current Contracts

- `risk-domain` owns deterministic metric calculation, rule evaluation and
  aggregate decision semantics.
- `RiskFactRequirements` in `trading-core` resolves facts from the selected
  policy, but currently treats equity as a policy-wide requirement and admits
  only `BROKER_REPORTED` by default for the generated requirement set.
- `RiskFactsProvider.EquityFact` already carries value, currency, provenance,
  availability, timestamps, quality flags, source and resolution method.
- `BrokerRiskFactsPort.Account` preserves that `EquityFact` at the Trading Core
  broker boundary.
- `BrokerModels.AccountFacts` also carries an `EquityFact`.
- `BrokerModels.AccountRiskFacts`, used by `RiskSnapshot`, currently carries
  only numeric account values and therefore cannot preserve the full equity
  evidence contract on that path.

## Risk Engine Dependencies

- `AccountSnapshot` requires non-null balance and equity and validates currency
  coherence.
- `ObservedMetricsCalculator` derives floating PnL as `equity - balance`.
- `ProjectionEngine` derives projected equity from current equity and known
  portfolio heat.
- `DerivedMetricsCalculator` uses balance for position risk, equity for
  exposure, daily baseline for daily drawdown and starting balance for total
  drawdown.
- `DailyDrawdownRule`, `MaximumTotalDrawdownRule` and
  `MaximumExposureRule` consume metrics that depend on equity.
- `RiskResultAggregator` already withholds a decision for `INCOMPLETE` or
  `FAILED` evaluations, but the current engine computes the shared metrics
  before rule traversal, so missing independent facts currently fail the whole
  traversal rather than producing per-rule incomplete results.
- `DeterministicRiskEngine` represents unsupported rules as failures/incomplete
  and uses `NOT_APPLICABLE` only when a rule does not support the current rule
  context. This existing representation must be checked before introducing a
  new enum.

## Validation and T1

- `TradePlanRiskEvaluationService` and
  `ExecutionTimeRiskRevalidationService` both resolve
  `RiskFactRequirements.from(rules)`, load facts with those requirements and
  enforce equity admissibility before constructing the Risk Domain snapshot.
- Both paths therefore need to share the invariant requirement resolution and
  preserve the same aggregate blocking semantics.
- T1 must not receive a relaxed requirement set merely because a partial result
  is useful diagnostically.

## Provider Mapping

- `KrakenRiskSnapshotCapability` obtains account balance/equity through
  `TradeBalance` and marks snapshot completeness/reasons during bounded capture.
- Broker adapters remain the correct place for provider decoding and evidence
  metadata. Trading Core must remain provider-neutral.
- cTrader-related missing equity remains out of scope and must continue to be
  represented as unavailable or inadmissible.

## Required Implementation Shape

1. Add an invariant evidence requirement representation close to the existing
   `RiskFactRequirements`, without introducing a generic rule framework.
2. Make the engine evaluate only rules whose evidence and metric inputs are
   complete, emitting explicit incomplete diagnostics for the others.
3. Ensure the aggregate result has no executable decision unless every
   mandatory applicable rule is complete and passes.
4. Extend the affected snapshot account contract so equity evidence is not lost.
5. Reuse the same requirement resolution in initial evaluation and T1.

## Risks

- Constructing `AccountSnapshot` with a synthetic equity to reuse existing
  calculators would violate the story safety constraints.
- Treating a missing metric as zero would create false approvals.
- Allowing `NOT_APPLICABLE` from missing facts would weaken policy semantics.
- Moving policy logic into Broker Service would violate provider neutrality.
- Changing the FTMO policy or its provenance requirements would exceed scope.

## Validation Plan

- Focused risk-domain tests for per-rule complete/incomplete behavior and
  aggregation.
- Trading Core tests for provenance, partial evaluation, T0/T1 consistency and
  no broker submission.
- Broker Service contract tests for preservation of account evidence metadata.
- Existing affected module suites and `git diff --check`.

## DevLog Context

DevLog applicability was checked using the project Story Agent with a stable
idempotency key. The initial request and same-key resume both timed out, so no
usable DevLog context was available. Repository evidence and accepted ADRs are
the authoritative sources for this analysis.
