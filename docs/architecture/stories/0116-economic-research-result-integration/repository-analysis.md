# Repository Analysis - Story 0116

## Status

Approved by the human engineer. Implementation has completed; this artifact
records the approved analysis baseline.

## Sources Reviewed

- Story 0116: `docs/architecture/stories/0116-economic-research-result-integration/story.md`
- `ADR-063 - Historical Order Book Evidence and Replay Semantics`: `Accepted`
- `ADR-064 - Historical Execution and Transaction-Cost Research Architecture`: `Accepted`
- Trading Core economic domain and tests
- Trading Core deterministic MARKET replay implementation from Story 0115
- Broker historical fee evidence foundation
- Market Data historical funding evidence model
- Market Intelligence objective outcome and dataset boundaries
- Trading Core PAPER and Risk execution boundaries

DevLog context was attempted for this Story but timed out. Repository evidence
is therefore authoritative for current implementation state. Existing unrelated
worktree changes were preserved and were not treated as Story 0116 changes.

## Repository State

- Current branch: `main`
- Current HEAD: `66caaf95a2d72764395e6a5c7fa3b4f1c338345c`
- `origin/main`: `66caaf95a2d72764395e6a5c7fa3b4f1c338345c`
- `git fetch origin`: attempted once and timed out.
- Worktree contains pre-existing modifications and untracked artifacts in Broker
  Service, Market Data, Market Intelligence, Docker configuration, and Stories
  0113/0114. They must remain untouched.
- Story 0115 is merged into `main` and its deterministic replay implementation is
  available.

## Architecture Reconciliation

### ADR-063

`ADR-063` remains compatible. Market Data owns normalized historical Order Book
evidence, strict sequence replay, `occurredAt`, `knownAt`, trust, and source
fingerprints. Trading Core consumes the provider-neutral replay result and owns
execution calculation.

### ADR-064

`ADR-064` is `Accepted` and remains compatible. It explicitly requires:

- Trading Core ownership of historical execution and economic calculation;
- separate reference movement, execution-adjusted economics, explicit costs, and
  aggregate EconomicResult;
- `UNKNOWN != ZERO`, `NOT_APPLICABLE != UNAVAILABLE`, and `GROSS != NET`;
- separate result availability and cost completeness dimensions;
- unavailable commission/funding to remain explicit;
- authoritative NetPnL/NetReturn only for a fully costed closed lifecycle;
- no changes to `ObservedMarketOutcome`, `research-dataset-v1`, or
  `feature-projection-v1`.

No `ADR_AMENDMENT_REQUIRED` condition was found.

## Existing Economic Foundations

### `StrategyIntent`

`trading-core/.../StrategyIntent.java` is the canonical provider-neutral intent.
It contains instrument, side, order type, quantity, and decision time. It has no
market ID, horizon, or lifecycle role. The historical lifecycle service must
receive market identity and explicit exit timing separately rather than create a
second intent type.

### `ReferenceMarketState`

`ReferenceMarketState` is the canonical provider-neutral market state. Story 0115
extended it with visible levels and replay provenance while preserving existing
constructors. The existing historical replay service constructs a
`LINEAR_PERPETUAL` state for the bounded MARKET replay.

### `SimulatedExecution`

`SimulatedExecution` is the canonical execution result. It preserves requested,
filled, and unfilled quantities, VWAP, status, execution timestamp, ordered
fills, reason, model version, source fingerprint, and execution fingerprint.
The replay calculator already handles full, partial, and unfilled visible-depth
execution without fabricating price.

### `EconomicResult`

`EconomicResult` already contains both the legacy single-leg fields and lifecycle
fields:

- entry and exit market states;
- entry and exit executions;
- reference market return;
- gross strategy PnL;
- execution-adjusted PnL;
- net PnL and net return;
- cost completeness and transaction costs;
- execution/cost model versions;
- deterministic calculation fingerprint.

Its constructor correctly rejects authoritative NetPnL on non-available,
incomplete, or non-fully-filled lifecycles. However, it does not expose a
dedicated `closedQuantity` or `residualExposure` field. Residual exposure can be
derived from entry and exit fills, but the Story requires it to remain explicit;
the implementation plan must choose the smallest compatible contract evolution
or a lifecycle result value object without creating a parallel economic domain.

### `EconomicLifecycleEvaluator`

`EconomicLifecycleEvaluator` is the existing pure lifecycle calculation
primitive. It validates:

- same instrument, market, and product;
- entry/exit sides and chronological timestamps;
- evidence known-at ordering;
- cost evidence eligibility;
- execution and transaction-cost policy versions;
- exit quantity not exceeding entry exposure;
- non-zero entry and exit fills.

It calculates reference return, reference-price gross PnL, execution-adjusted
P&L, lifecycle status, completeness, and a deterministic fingerprint. It returns
`DEGRADED` with non-null execution-adjusted PnL when the lifecycle is executable
but not fully costed or fully closed, which matches ADR-064's separate
availability/completeness model.

Current limitations relevant to Story 0116:

- it accepts already-created entry/exit executions but does not orchestrate
  replay;
- it has no explicit holding horizon or exit cutoff parameter;
- it calculates cost eligibility at entry and exit execution times, but the
  application boundary must also enforce the research/cost cutoff policy;
- `netReturn` is not calculated, consistent with the absence of a canonical
  denominator policy;
- the current aggregate status is `DEGRADED` for incomplete costs, while the
  component `executionAdjustedPnl` remains available;
- residual exposure is not a named result field.

### `TransactionCosts`, `ExplicitCost`, and `CostEvidence`

`ExplicitCost` distinguishes:

- `AVAILABLE`;
- `UNAVAILABLE`;
- `NOT_APPLICABLE`;
- `INSUFFICIENT_EVIDENCE`;
- `UNSUPPORTED`.

`TransactionCosts.completeness()` derives `FULLY_COSTED`, `PARTIALLY_COSTED`, or
`INSUFFICIENT_EVIDENCE` from component states. It does not convert unavailable
amounts to zero. `NOT_APPLICABLE` contributes zero only because it is explicitly
not applicable.

The legacy `TransactionCosts` constructor accepts numeric fee/funding values and
maps them to available costs. The new lifecycle path must use explicit cost
components, not that constructor, for unavailable historical commission or
funding.

### `MarketOrderEconomicEvaluator`

`MarketOrderEconomicEvaluator` is a legacy single-leg top-of-book evaluator that
requires numeric fee and funding inputs. It is not suitable for this Story's
multi-leg historical replay path and must not be used to reconstruct missing
costs or replace the visible-depth replay.

## Existing Execution Replay

`HistoricalMarketExecutionService` is the Story 0115 application service. It:

- calls the provider-neutral `HistoricalOrderBookPort` with depth 25;
- enforces MARKET-only and zero-latency `decisionAt == executionCutoff`;
- validates replay market identity, depth, cutoff, provenance, ordinal range,
  and temporal admissibility;
- builds `ReferenceMarketState` from the replay;
- delegates visible-depth book walking to
  `VisibleDepthMarketExecutionCalculator`.

It currently returns a linear-perpetual execution result and has no lifecycle
concept. The new service must call it independently for entry and exit, creating
an explicit opposite-side exit `StrategyIntent` whose quantity is the actual
entry filled quantity.

The exit must not reuse entry state, current prices, OHLC, PAPER settlement, or
the operational broker execution path.

## Holding Horizon

`market-intelligence` contains `OutcomeHorizon(Duration)` with one-hour,
four-hour, and one-day helpers, but that type belongs to objective future market
outcomes and must not be imported into Trading Core's economic lifecycle.

Trading Core has planning horizons (`INTRADAY`, `SWING`, `POSITION`) but those are
not explicit historical durations. No canonical Trading Core economic holding
horizon exists. The safest bounded integration boundary is therefore an explicit
`exitCutoff` supplied by the caller, with any optional duration-to-cutoff
calculation kept outside the economic domain or introduced only if the approved
plan establishes a repository-consistent value object.

## Cost Evidence Boundaries

Broker Service contains a provider-neutral `HistoricalFeeScheduleReader` and
historical fee schedule persistence foundations. There is no existing Trading
Core cost-evidence client/port that resolves fee schedules for this lifecycle.
Market Data contains a historical funding evidence model, but no Trading Core
funding cost resolver exists and Story 0114 remains an acquisition boundary.

This Story must not implement fee or funding acquisition. The first lifecycle
integration should accept explicit `TransactionCosts` or an existing canonical
cost-resolution boundary if one is identified during planning. In the current
repository, explicit cost components are the available provider-neutral input:

- perpetual tests use `ExplicitCost.unavailable()` for commission/funding;
- spot tests may supply available commission and `ExplicitCost.notApplicable()`
  funding;
- no current Kraken schedule or unrelated actual trade fee may be reused.

## Calculation Semantics

The existing lifecycle primitive uses:

```text
referenceMarketReturn = direction(exit reference - entry reference) / entry reference
grossStrategyPnl      = direction(exit reference - entry reference) * closed quantity
executionAdjustedPnl  = direction(exit VWAP - entry VWAP) * closed quantity
```

This matches the Story and the existing linear/spot direction abstraction. The
implementation must reuse this primitive rather than `TradingCalculatorService`
for the research result; the latter belongs to mutable operational trade/account
flows and uses legacy `TradeType` semantics.

`EconomicResult.netReturn` remains unavailable unless a canonical denominator is
found. The Story does not authorize inventing one.

## Boundaries To Preserve

- `ObservedMarketOutcome` remains objective future market evolution only.
- `research-dataset-v1` and `feature-projection-v1` remain unchanged.
- PAPER settlement and simulated account mutation remain unchanged.
- Risk authorization and operational execution remain unchanged.
- Market Data replay semantics remain unchanged.
- Broker fee acquisition remains outside this Story.
- Funding acquisition remains outside this Story.
- No public API or persistence is currently required by ADR-064.

## Main Risks

1. The current `EconomicResult` supports lifecycle data but does not name
   residual exposure; adding fields risks breaking the existing record
   construction surface. The plan must preserve existing constructors/callers or
   introduce a minimal compatible lifecycle representation.
2. The existing replay service is hard-coded to linear perpetual for Story 0115.
   The Spot fully-costed scenario should remain a domain/evaluator reference test
   unless a broader product-type replay policy is approved.
3. Cost evidence acquisition is not available in Trading Core. Treating supplied
   numeric costs as historical evidence would violate ADR-064.
4. Entry partial fills require the exit intent quantity to be the entry filled
   quantity. The orchestration must never reuse requested entry quantity.
5. Partial exits must produce a degraded/open lifecycle without NetPnL for the
   entire requested intent and without synthetic closure.
6. Full module verification may depend on PostgreSQL/Testcontainers availability,
   as observed during Story 0115 validation.

## Proposed Implementation Boundary

Subject to Implementation Plan approval:

- add a bounded Trading Core application service for historical economic
  evaluation;
- accept market ID, canonical entry `StrategyIntent`, explicit entry cutoff,
  explicit exit cutoff, and explicit `TransactionCosts`;
- call the existing historical MARKET replay service for entry and exit;
- derive the exit side and quantity from the entry intent/fill;
- delegate all calculation to `EconomicLifecycleEvaluator`;
- evolve the canonical lifecycle result only as required to expose closed and
  residual quantities deterministically;
- add focused tests for full/partial/no-fill lifecycle, long/short direction,
  incomplete/complete costs, temporal eligibility, and fingerprint stability;
- add no fee/funding acquisition, persistence, API, dataset, PAPER, or Risk
  changes.

## Validation Plan

- Focused Trading Core lifecycle tests.
- Existing economic evaluator tests.
- Existing deterministic MARKET replay tests.
- Relevant Trading Core regression tests for PAPER and execution boundaries.
- `./mvnw -q test` or the repository-standard affected-module test command.
- `./mvnw verify` where integration dependencies are available.
- `git diff --check` and complete diff/status review.
- Runtime authenticated proof only if entry and exit replay evidence are healthy;
  otherwise report the blocker without using anonymous or current-state fallback.

## Analysis Decision

```text
ARCHITECTURE_BLOCKER = NONE
ADR_AMENDMENT_REQUIRED = NO
IMPLEMENTATION_SCOPE = TRADING_CORE_APPLICATION_AND_DOMAIN_TESTS
MARKET_DATA_SEMANTIC_CHANGE = NO
BROKER_FEE_ACQUISITION = OUT_OF_SCOPE
FUNDING_ACQUISITION = OUT_OF_SCOPE
PERSISTENCE_REQUIRED_BY_CURRENT_ADR = NO
IMPLEMENTATION_READY_AFTER_PLAN_APPROVAL = YES
```
