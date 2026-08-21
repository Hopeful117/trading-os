# Story 0008 — On-Demand Market Snapshot Acquisition

## Metadata

**ID:** `0008`  
**Title:** On-Demand Market Snapshot Acquisition  
**Status:** Implemented locally

## Goal

Remove the runtime dependency between ACTIVE analysis and a previously primed
websocket ticker subscription.

After this Story, a cold runtime must be able to execute:

`ActiveScan`  
↓  
`AnalysisExecution(ACTIVE)`  
↓  
`MarketSnapshotContextContributor`  
↓  
`Market Data` cache lookup  
↓  
one-shot provider acquisition when required  
↓  
normalized current snapshot  
↓  
`ExecutionPlanner`  
↓  
capability execution  
↓  
`ProductionIntelligencePipeline`

without requiring the caller or another component to manually subscribe the
market ticker first.

## Context

Story 0006 introduced durable Active Scan orchestration and Story 0007 made the
scan lifecycle observable and trader-readable.

Runtime validation after Story 0007 then proved a deeper defect in the ACTIVE
analysis path:

- `AnalysisExecution(ACTIVE)` was created correctly;
- the execution reached context construction and planning;
- the planning stage failed before any `PipelineRun` was created;
- the common error was `NO_COMPATIBLE_ARTIFACT_PRODUCER` for
  `normalized-market-snapshot`;
- the underlying reason was not the absence of a conceptual producer, but the
  absence of current market data in the live ticker cache;
- manually subscribing a market ticker before analysis made the same downstream
  intelligence pipeline succeed.

That runtime behavior was incompatible with the intended semantics of
on-demand Active Scanner orchestration.

## Problem

The previous current-price/snapshot path was effectively cache-only:

`MarketPriceSnapshotService`  
↓  
`TickerEventPublisher.latestByMarketId(...)`

If the websocket ticker cache had not already been populated, Market Data
returned `PRICE_UNAVAILABLE`, Market Intelligence treated the market snapshot as
missing context, and capability planning failed with a misleading architectural
producer error.

This made ACTIVE analysis depend on prior websocket priming, which violated the
intended Market Data ownership boundary.

## User / Product Value

This Story allows Trading OS to request current market state on demand.

Practical effect:

- a trader can launch Active Scanner in a cold runtime;
- the analysis no longer depends on hidden subscription warm-up;
- Market Intelligence continues to consume normalized market context rather
  than provider mechanics;
- Story 0006 orchestration and Story 0007 projection become materially more
  useful in real runtime conditions.

This improves the real-world short-session trader workflow by removing a hidden
operational prerequisite that made ACTIVE analysis unreliable.

## Dependencies

- `ADR-033` — Active and Passive Market Intelligence Orchestration
- Story 0006 — Persistent Active Scan Orchestration Foundation
- Story 0007 — Persistent Active Scan Lifecycle Reconciliation & Result
  Projection

## Scope

Included:

- evolve current snapshot acquisition from cache-only to cache-first with
  one-shot provider fallback;
- keep one normalized current-state cache shared by websocket and REST-derived
  snapshots;
- make Market Data the authority for snapshot freshness;
- replace fragmented snapshot statuses with truthful normalized semantics;
- add bounded on-demand provider acquisition through the existing Market Data
  provider boundary;
- add small per-market in-flight acquisition deduplication;
- preserve provider-independent snapshot responses to Market Intelligence;
- map unavailable and stale current-state conditions into bounded ACTIVE
  analysis failures instead of misleading producer-architecture failures;
- keep websocket streaming behavior intact for continuous monitoring paths;
- centralize Kraken provider-symbol resolution where required by one-shot
  snapshot and OHLC retrieval;
- add focused tests and runtime validation.

## Out of Scope

- Active Scanner orchestration redesign;
- Story 0006 idempotency, dispatch or child-linkage redesign;
- Story 0007 reconciliation redesign;
- temporary websocket-subscription fallback for ACTIVE analysis;
- Passive Scanner redesign;
- distributed cache or Redis;
- Kafka or event-bus introduction;
- distributed locking;
- Trade Plan redesign;
- Risk Domain redesign;
- Broker redesign;
- challenge progress;
- Quant Research, Strategy, Backtest or AI features.

## Snapshot Contract

Story 0008 intentionally evolves the existing normalized `MarketPriceSnapshot`
contract rather than creating a scanner-specific snapshot model.

The resulting snapshot semantics expose:

- `marketId`
- `symbol`
- `lastPrice`
- `bid`
- `ask`
- `tradable`
- `occurredAt`
- `capturedAt`
- `status`
- `sourceSnapshotId`
- `sourceSnapshotVersion`

For deterministic ACTIVE spread analysis:

- `bid` and `ask` are mandatory for a usable current snapshot;
- stale or unavailable current-state must not be silently presented as fresh;
- no provider-specific DTO or symbol must leak out of Market Data.

## Freshness Semantics

Story 0008 establishes Market Data as the authority for current snapshot
freshness.

Normalized V1 semantics:

- `FRESH`
- `STALE`
- `UNAVAILABLE`
- `UNKNOWN_MARKET` remains a distinct request-validation outcome.

Freshness is evaluated from the normalized observation time using a Market
Data-owned TTL.

Configured default:

- `market-data.snapshot.stale-after=30s`

## Acquisition Algorithm

Implemented V1 algorithm:

1. validate requested market;
2. read current-state cache;
3. if cached snapshot is fresh, return `FRESH`;
4. otherwise perform one-shot provider acquisition through Market Data;
5. if acquisition succeeds, normalize it, update the shared current-state
   cache, and return `FRESH`;
6. if acquisition fails and a previously cached usable snapshot exists, return
   that snapshot truthfully as `STALE`;
7. if no usable current-state exists, return `UNAVAILABLE`.

## Failure Semantics

The Story replaces the misleading runtime behavior where a missing current
snapshot surfaced as:

- `NO_COMPATIBLE_ARTIFACT_PRODUCER`

for a producer that actually existed.

Instead, bounded data-availability failures are surfaced earlier in the ACTIVE
analysis flow:

- `MARKET_SNAPSHOT_UNAVAILABLE`
- `MARKET_SNAPSHOT_STALE`

Provider-specific details remain inside Market Data logs and adapters.

## Security / Boundary Requirements

Story 0008 preserves the existing architecture:

- Active Scanner remains an orchestration layer;
- Market Intelligence consumes normalized market context;
- Market Data owns current-state acquisition, freshness, and provider-specific
  mapping;
- no Kraken-specific logic is moved into Market Intelligence;
- no change is made to Story 0006 actor ownership, Gateway header propagation,
  or Story 0007 trader-facing projection ownership.

## Acceptance Criteria

Minimum acceptance:

1. cold runtime with no manual ticker subscription;
2. Market Data one-shot acquisition provides a normalized `FRESH` snapshot;
3. `MarketSnapshotContextContributor` receives usable current-state context;
4. `ExecutionPlanner` no longer fails because `normalized-market-snapshot`
   cannot be produced;
5. ACTIVE analysis progresses into capability execution;
6. `ProductionIntelligencePipeline` is reached;
7. `PipelineRun` can be persisted when downstream logic allows;
8. Story 0007 projection remains compatible with downstream outcomes.

## Non-Functional Requirements

- same-market concurrent cold requests must not cause an obvious provider
  stampede in one process;
- different markets remain independent;
- stale data must remain distinguishable from fresh data;
- provider failures must be bounded and provider-independent at the consuming
  layer;
- websocket and REST one-shot acquisitions must converge into the same
  normalized current-state representation;
- no migration is required.

## Remaining Limitations

- the full authenticated Gateway-to-ActiveScan runtime benchmark remains partly
  blocked in the current workstation environment by an unrelated `trading-core`
  runtime/schema problem;
- provider-specific runtime diagnostics remain bounded rather than fully
  persisted into a richer failure model;
- no distributed cache or distributed acquisition deduplication is introduced
  in this Story.
