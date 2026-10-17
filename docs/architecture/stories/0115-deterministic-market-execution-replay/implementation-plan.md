# Implementation Plan - Story 0115

## Status

Draft - pending human approval.

This plan implements the approved Story 0115 within ADR-063 and ADR-064. It
does not authorize production changes until the human engineer approves this
plan.

## Implementation Boundary

Production changes remain bounded to:

- `trading-core` for the provider-neutral historical execution port, adapter,
  market-state assembly, deterministic MARKET book walking, and execution
  result evolution;
- `trading-core` tests for the pure execution and integration boundaries;
- `market-data` only if a replay response contract correction is required by
  existing behavior. The current replay endpoint and response already contain
  the required evidence provenance, so no Market Data production change is
  currently planned.
- this Story's canonical documentation artifacts.

No Market Intelligence, Broker Service, PAPER, Risk, funding, fee, dataset,
ObservedMarketOutcome, or public API implementation is included.

## Decisions Fixed by This Plan

### 1. Execution Policy

Use one immutable semantic model version:

```text
visible-depth-market-execution-v1
```

V1 uses zero modeled latency and therefore requires:

```text
executionCutoff == StrategyIntent.decisionAt
```

This is a policy assumption, not an observed exchange latency measurement.

V1 supports only `StrategyIntent.OrderType.MARKET`.

### 2. Replay Input

Trading Core obtains replay through a provider-neutral application port:

```text
HistoricalOrderBookPort.replay(marketId, depth, executionCutoff)
```

The port returns a provider-neutral replay result containing status, normalized
levels, cutoff, sequence, ordinals, timestamps, provider provenance,
normalization version, source evidence fingerprint, and reason.

The infrastructure adapter calls the existing authenticated internal Market Data
endpoint:

```text
GET /internal/v1/order-book/replay/{marketId}?depth=25&asOf=<cutoff>
```

The adapter uses the existing service-JWT Feign configuration. No endpoint is
made public and no authentication control is weakened.

### 3. Canonical Market-State Evolution

Extend `ReferenceMarketState` minimally for available historical execution
states with:

- normalized visible bid levels;
- normalized visible ask levels;
- replay sequence identity;
- source ordinal range;
- provider and provider-symbol provenance;
- normalization version.

Existing constructors remain available for current economic tests and callers.
The new constructor validates the additional fields. Invalid/unavailable replay
does not construct an executable state; it produces an explicit unavailable
execution result instead.

No Market Data evidence entity or replay model is copied into Trading Core.

### 4. Canonical Execution Result Evolution

Evolve `SimulatedExecution` rather than introducing a parallel execution type.
The result will preserve:

- requested quantity;
- filled quantity;
- derived unfilled quantity;
- average execution price, absent when unfilled;
- ordered per-level fills;
- fill execution timestamp;
- side and execution status;
- explicit reason when degraded or unsupported;
- execution model version;
- source evidence fingerprint;
- deterministic execution fingerprint.

Existing constructors remain available for current tests and lifecycle callers.
New fields are included in deterministic lifecycle material where appropriate.

### 5. Status Mapping

Use existing status values only:

| Condition | Status |
| --- | --- |
| Valid book and requested quantity consumed | `FILLED` |
| Valid book, some visible quantity consumed, remainder exists | `PARTIALLY_FILLED` and reason `INSUFFICIENT_DEPTH` |
| Valid book, no eligible opposite-side level | `UNFILLED` |
| Valid replay unavailable or invalid | `INSUFFICIENT_MARKET_EVIDENCE` |
| MARKET intent with unsupported order type | `UNSUPPORTED_ORDER_TYPE` |
| Cutoff/state/level/fingerprint input violation | `INVALID_INPUT` or `STALE_EVIDENCE` according to the failing invariant |

No new execution status is introduced.

### 6. Book-Walking Semantics

Implement a pure calculator with no Spring, network, clock, persistence, or
mutable global state.

```text
BUY  -> asks ascending by price
SELL -> bids descending by price
```

At each level:

```text
consumed = min(level.quantity, remaining requested quantity)
```

Stop when requested quantity is satisfied or visible levels are exhausted.
Compute VWAP only when filled quantity is positive, using `BigDecimal` with
`MathContext.DECIMAL128` and repository conventions.

The calculator receives already normalized levels but defensively validates
ordering, positive prices, non-negative quantities, and non-crossed top-of-book
state where required by the canonical market state.

### 7. Fingerprinting

Compute the execution fingerprint from length-delimited canonical semantic
material containing:

- market and instrument identity;
- product type;
- side, order type, and requested quantity;
- decision time and execution cutoff;
- replay sequence identity and source evidence fingerprint;
- source ordinal range;
- normalized visible levels used by the result;
- consumed per-level fills;
- normalization version;
- execution model version;
- result status and reason.

Exclude database IDs, random UUIDs, request IDs, scheduler timestamps, and
current wall-clock time. Repeating the same semantic inputs must produce the
same result and fingerprint.

### 8. Costs and Other Boundaries

The replay service does not call `MarketOrderEconomicEvaluator`, calculate
commission, calculate funding, or create `TransactionCosts`. A valid execution
can therefore exist while economic cost completeness remains unavailable.

The existing evaluator remains a separate top-of-book economic foundation and
is not silently changed into a book-walking evaluator by this Story.

## Expected Production Files

### Trading Core Domain

- `execution/domain/economic/ReferenceMarketState.java`
  - add visible replay levels and provenance through the smallest compatible
    extension.
- `execution/domain/economic/SimulatedExecution.java`
  - add immutable fills, reason, and execution fingerprint while preserving
    existing construction paths.
- `execution/domain/economic/SimulatedExecutionFingerprint.java`
  - add only if repository conventions favor isolating deterministic hash
    material; otherwise keep the calculation in the pure calculator.
- `execution/domain/economic/HistoricalMarketOrderExecution.java`
  - add only if an application service needs a distinct orchestration result;
    do not create a second execution result type if `SimulatedExecution` can
    carry the canonical result.

### Trading Core Application and Infrastructure

- `execution/application/port/HistoricalOrderBookPort.java`
  - provider-neutral replay request/result boundary.
- `execution/application/service/HistoricalMarketExecutionService.java`
  - validates cutoff/order type, obtains replay, assembles state, and delegates
    to the pure calculator.
- `execution/domain/economic/VisibleDepthMarketExecutionCalculator.java`
  - pure MARKET book-walking and VWAP calculation.
- `execution/infrastructure/adapter/MarketDataHistoricalOrderBookAdapter.java`
  - maps the internal Market Data response to the application port.
- `market_data/apiClient/MarketDataClient.java`
  - extend the existing Feign boundary with the internal replay response only
  if this is the smallest compatible adapter shape.
- `market_data/dto/HistoricalOrderBookReplayDto.java`
  - add only if nested Feign response records would violate existing local
    conventions.

The final file list may be smaller after implementation inspection. No new
shared module is planned.

## Implementation Sequence

1. Add provider-neutral replay port types and map the existing response without
   leaking Market Data or Kraken classes into the domain.
2. Extend `ReferenceMarketState` and `SimulatedExecution` minimally, preserving
   existing constructors and lifecycle behavior.
3. Implement the pure visible-depth calculator and canonical fingerprint.
4. Implement the application service that enforces MARKET-only and zero-latency
   cutoff policy, handles replay statuses, and invokes the calculator.
5. Add focused unit tests for the calculator and domain invariants.
6. Add adapter/port contract tests for available, invalid, unavailable, and
   malformed replay responses.
7. Add Trading Core integration coverage proving service-JWT internal replay
   access is used without changing Market Data security.
8. Run a real authenticated historical replay against persisted evidence if the
   runtime path is available; do not mutate evidence or use anonymous endpoint
   access.
9. Re-check PAPER, Risk, Market Intelligence, and frozen dataset boundaries.
10. Reconcile documentation and produce the implementation report.

## Test Plan

### Pure Calculator Tests

- single-level full BUY;
- multi-level full BUY and exact VWAP;
- multi-level full SELL and descending priority;
- mandatory 2 BTC request against 1.7 BTC visible asks;
- empty opposite side with zero fill and absent price;
- level deletion/zero quantity already reflected by replayed state;
- invalid ordering, price, quantity, and crossed-book inputs;
- quantity precision using `BigDecimal`;
- no floating-point arithmetic.

### Domain and Fingerprint Tests

- full-fill invariants;
- partial-fill positive remainder;
- unfilled execution without price;
- unsupported order type;
- execution reason preservation;
- same semantic input produces the same fills, VWAP, status, and fingerprint;
- changed quantity changes the result/fingerprint;
- changed book changes the result/fingerprint;
- changed model version changes the fingerprint;
- random/database/time values do not affect identity.

### Temporal and Replay Contract Tests

- cutoff must equal decision time under zero-latency V1;
- replay `knownAt > cutoff` cannot be consumed;
- invalid replay cannot produce a fill;
- unavailable replay cannot produce a zero-price result;
- checkpoint snapshot followed by deltas replays deterministically;
- source sequence and evidence fingerprint are retained;
- no current ticker or current book fallback exists.

### Regression Tests

- existing `MarketOrderEconomicEvaluatorTest`;
- existing `EconomicLifecycleEvaluatorTest`;
- existing PAPER execution regression suite;
- existing Market Data `OrderBookReplayServiceTest`;
- relevant Trading Core architecture tests.

## Validation Commands

Initial focused validation:

```bash
cd trading-core && ./mvnw -q -Dtest=<new replay tests> test
cd market-data && ./mvnw -q -Dtest=OrderBookReplayServiceTest test
```

Module validation:

```bash
cd trading-core && ./mvnw verify
cd market-data && ./mvnw verify
```

Also run applicable SonarQube validation, `git diff --check`, and a complete
review of the diff. Existing unrelated worktree changes must remain untouched.

## Runtime Proof

Use the known runtime market ID only through authenticated service-to-service
access:

```text
marketId: e23ba62a-995f-4755-8deb-11a71ab3788b
depth:    25
symbol:   PF_XBTUSD
```

Choose a cutoff with valid persisted evidence. Report the semantic result,
provenance, and fingerprints without secrets. Repeat the same replay and verify
identical output. A real partial fill is demonstrated only if safely available;
the deterministic unit test remains mandatory regardless.

## Documentation Reconciliation

Update only the Story 0115 implementation report and other canonical Story
artifacts required by the repository workflow. Document:

- MARKET-only and visible-depth behavior;
- zero-latency policy version;
- partial-fill/remainder semantics;
- no queue, latency calibration, or market-impact claim;
- independent unavailable fee and funding states;
- runtime proof and validation outcomes.

Do not amend ADR-063 or ADR-064 unless a genuine contradiction is discovered.

## Plan Decision

```text
ARCHITECTURE_BLOCKER = NONE
PLAN_REQUIRES_ADR_AMENDMENT = NO
PLAN_REQUIRES_MARKET_DATA_SEMANTIC_CHANGE = NO
PLAN_REQUIRES_PAPER_OR_RISK_CHANGE = NO
IMPLEMENTATION_READY_AFTER_HUMAN_PLAN_APPROVAL = YES
```
