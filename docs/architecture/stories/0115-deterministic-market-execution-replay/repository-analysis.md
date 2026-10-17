# Repository Analysis - Story 0115

## Status

Repository Analysis prepared after human approval of Story 0115. This artifact
does not approve implementation or the next workflow stage.

## Repository Baseline

```text
HEAD:        5538a44baa0fcced66162ce62035103f566b0b70
Branch:      main
origin/main: 5538a44baa0fcced66162ce62035103f566b0b70
```

The worktree contains pre-existing changes in Broker Service, Market Data,
Market Intelligence, Docker configuration, and Story 0113/0114 artifacts. They
were preserved and are outside this Story's scope.

`git fetch origin` was attempted but timed out. No implementation files were
modified while preparing this analysis.

DevLog applicability was checked. The request failed because the configured
provider has no remaining credits. Repository and accepted ADR evidence remain
the authoritative sources for this analysis.

## Architecture Verification

| Artifact | Status | Relevant decision |
| --- | --- | --- |
| ADR-063 | Accepted | Market Data owns strict historical Order Book evidence and replay. |
| ADR-064 | Accepted | Trading Core owns deterministic historical execution calculation. |
| Story 0112A | Approved | Durable bounded Kraken `PF_XBTUSD` Order Book evidence and replay foundation. |
| Story 0112B | Approved | Market Intelligence consumes replay for liquidity interpretation; it does not own execution. |
| Story 0113 | Approved | Economic foundation exists; replay and cost acquisition are follow-up work. |
| Story 0114 | Blocked | Funding acquisition is separate and remains outside this Story. |

No contradiction was found between Story 0115, ADR-063, and ADR-064.

## Current Order Book Evidence

The active runtime database was inspected read-only using a repeatable-read
transaction. At the measurement point:

```text
market:          BTC/USD
provider:        KRAKEN
provider symbol: PF_XBTUSD
product:         LINEAR_PERPETUAL
depth:           25
evidence rows:   2,222,213
snapshots:       31
deltas:         2,222,182
trusted rows:    2,222,213
checksum rows:   0
sequences:       1 ACTIVE
earliest event:  2026-10-17T12:26:58.585Z
latest event:    2026-10-17T14:30:44.517Z
earliest known:  2026-10-17T12:26:58.671882Z
latest known:    2026-10-17T14:53:19.645945Z
```

The data was still being captured during inspection. Counts and latest
timestamps are therefore a point-in-time health measurement, not a fixed
fixture.

The runtime market identity is:

```text
marketId:            e23ba62a-995f-4755-8deb-11a71ab3788b
provider:            KRAKEN
symbol:              BTC/USD
providerSymbol:      PF_XBTUSD
productType:         LINEAR_PERPETUAL
tickSize:            1.00
quantityPrecision:   4
pricePrecision:      0
tradingStatus:       OPEN
tradable:            true
```

The sequence contains duplicate ordinal groups for checkpoint snapshots. This
is consistent with the existing checkpoint design because a checkpoint is a
derived snapshot at the current sequence ordinal. The replay implementation
must continue to select a valid starting snapshot and then apply only later
DELTA evidence from the same sequence. The implementation plan must include
regression coverage for checkpoint selection and ordinal handling.

The anonymous HTTP replay request returned `403`, as expected. The internal
endpoint requires `X-Service-Authorization` with a service JWT. Trading Core
already has the Feign/JWT pattern needed for internal Market Data calls.

## Market Data Replay Boundary

### Existing API

```text
GET /internal/v1/order-book/replay/{marketId}?depth=25&asOf=<T0>
```

The endpoint is implemented by:

- `market-data/.../InternalOrderBookReplayController.java`;
- `market-data/.../OrderBookReplayService.java`;
- `market-data/.../OrderBookReplayResult.java`.

The response already contains:

- replay status and reason;
- market ID and depth;
- sequence ID;
- evaluation cutoff;
- latest `occurredAt` and `knownAt`;
- source ordinal range;
- provider and provider symbol;
- normalization version;
- aggregate source evidence fingerprint;
- normalized bids and asks.

`OrderBookEvidenceRepository.findTrusted` enforces:

```sql
trusted = true
AND sequence.status = 'ACTIVE'
AND evidence.known_at <= cutoff
```

`OrderBookReplayService` reconstructs bids in descending order and asks in
ascending order using `TreeMap`, applies zero quantities as deletions, rejects
non-contiguous DELTAs, and fails when no trusted snapshot exists.

### Trust Limitation

`OrderBookEvidenceCaptureService` currently accepts an explicit checksum only
when it is `null`:

```java
return delta.checksum() == null;
```

The current Futures adapter does not implement checksum validation. Persisted
runtime evidence consequently has no checksum values and is trusted through
the current sequence/persistence boundary. This is an explicit limitation of
the existing evidence foundation. Story 0115 must not weaken it or claim
checksum validation that does not exist. It should consume replay status as
provided and fail closed for invalid/unavailable replay.

## Existing Trading Core Economic Contract

### `StrategyIntent`

Current fields:

```text
instrument, side, orderType, quantity, decisionAt
```

It supports `MARKET` and `LIMIT`. The historical replay service must reject
non-MARKET intents without changing operational order types.

### `ReferenceMarketState`

Current fields include:

```text
marketId, instrument, evaluationCutoff, bid, ask, referencePrice,
productType, knownAt, evidenceFingerprint
```

It validates positive top-of-book prices, a non-crossed quote, and temporal
identity. It does not yet carry visible level lists, replay sequence identity,
depth, or source range. The implementation plan must choose the smallest
compatible way to preserve visible replay state without creating a parallel
economic domain.

### `SimulatedExecution`

Current fields include:

```text
side, requestedQuantity, filledQuantity, averageExecutionPrice,
executedAt, status, executionModelVersion, sourceFingerprint
```

It already validates full, partial, and unfilled status invariants and derives
`unfilledQuantity()`. It does not preserve individual consumed levels or an
explicit status reason. The Story requires the minimum compatible evolution to
retain per-level fills and deterministic provenance.

### Status Vocabulary

The existing enum already contains:

```text
FILLED
PARTIALLY_FILLED
UNFILLED
INSUFFICIENT_MARKET_EVIDENCE
INSUFFICIENT_DEPTH
UNSUPPORTED_ORDER_TYPE
STALE_EVIDENCE
INVALID_INPUT
```

No duplicate status should be introduced. The implementation plan must map
invalid/unavailable replay to the existing vocabulary consistently.

### Economic Boundaries

`MarketOrderEconomicEvaluator` currently performs single-price top-of-book
execution and requires fee/funding evidence. It is not a suitable book walker
and must not be broadened into a fee or funding implementation in this Story.

`TransactionCosts` already distinguishes explicit cost component status and
cost completeness. The replay path must not call the evaluator with invented
fee or funding values merely to produce an execution result.

The existing PAPER adapter remains a separate operational path using current
bid/ask state and must not be rewired.

## Service Ownership and Integration Boundary

Market Intelligence already has an internal `OrderBookReplayClient` and
`OrderBookContext`, but ADR-064 explicitly assigns execution replay calculation
to Trading Core. Trading Core must not call Market Intelligence for execution or
reuse its domain types, because that would invert ownership and risk a runtime
cycle.

Trading Core already uses OpenFeign and has `MarketDataFeignConfiguration` with
service JWT authorization for `/internal/**`. The smallest compatible boundary
is a Trading Core provider-neutral Market Data replay port backed by an internal
Feign adapter. It may extend or sit beside the current `MarketDataClient`, but
the domain execution calculator must depend on the port, not Feign or DTOs.

No public API or Gateway route is required by this Story.

## Implementation Shape Recommended for Planning

The implementation plan should separate these concerns:

1. A provider-neutral replay response/port in Trading Core, mapped from the
   existing Market Data endpoint.
2. A provider-neutral executable market-state value carrying the valid replay
   levels and provenance, reusing/evolving `ReferenceMarketState` rather than
   creating a second economic model.
3. A pure deterministic MARKET book-walking primitive in Trading Core.
4. Minimal evolution of `SimulatedExecution` to preserve per-level fills,
   remainder, and provenance/fingerprint requirements.
5. An application-level assembler/service that obtains strict replay and invokes
   the pure calculator.
6. Focused tests for full, partial, empty, invalid, unsupported, temporal,
   precision, provenance, and determinism cases.

The implementation must not modify Market Data evidence semantics, fees,
funding, PAPER, Risk, objective outcomes, or research datasets.

## Risks and Open Decisions for the Implementation Plan

1. **Canonical result evolution:** decide whether per-level fills belong directly
   in `SimulatedExecution` or in a small existing-compatible value object.
2. **Replay failure mapping:** map `AVAILABLE`, `INVALID`, and `UNAVAILABLE` to
   the existing Trading Core status vocabulary without inventing statuses.
3. **Reference state shape:** preserve `knownAt`, cutoff, sequence, depth,
   normalization, and source fingerprints without duplicating Market Data's
   evidence model.
4. **Checkpoint ordinals:** add a regression test proving that checkpoint
   snapshots do not cause false replay invalidation and that only later deltas
   are applied.
5. **Runtime proof access:** use authenticated service-to-service access or a
   direct application-level integration test; do not weaken the endpoint's
   security to support manual curl.
6. **Market constraints:** inspect the canonical market constraint contract and
   enforce only constraints already required by the existing execution policy.
7. **Checksum limitation:** preserve the existing explicit limitation and do not
   claim provider checksum validation in the replay result.

## Validation Baseline

The following focused baseline tests passed without Story 0115 implementation
changes:

```text
market-data:  ./mvnw -q -Dtest=OrderBookReplayServiceTest test
trading-core: ./mvnw -q -Dtest=MarketOrderEconomicEvaluatorTest,EconomicLifecycleEvaluatorTest test
```

The Mockito self-attachment warning was emitted by the Market Data test run; it
did not fail the test command.

## Analysis Decision

```text
ARCHITECTURE_BLOCKER = NONE
IMPLEMENTATION_PLAN_REQUIRED = YES
IMPLEMENTATION_AUTHORIZED = NO
```

The repository is ready for an Implementation Plan after human approval of this
Repository Analysis. The plan must resolve the open decisions above before any
production code is modified.
