# Story 0112A - Durable Order Book Evidence Acquisition

## Metadata

**ID:** `0112A`

**Title:** Durable Order Book Evidence Acquisition

**Status:** Approved

---

## Goal

Capture a bounded, truthful, provider-neutral historical Order Book evidence
stream for the configured Kraken BTC/USD linear perpetual market so that Trading
OS can later reconstruct the latest valid book known at an explicit evaluation
cutoff.

This Story establishes the durable evidence and replay foundation. It does not
implement liquidity intelligence or derived Order Book features.

---

## Context

Market Data already receives Kraken Order Book snapshots and incremental updates,
reconstructs an in-memory book, publishes the latest snapshot, and exposes a live
reactive stream. That state is operational and is not durable research evidence.

ADR-063 defines the accepted architecture for immutable normalized snapshots and
deltas, reconstruction sequences, checksum trust, reconnect recovery, bounded
checkpoints, and strict replay. It complements the temporal and provenance rules
established by ADR-057, ADR-058, and ADR-060.

The initial operational scope is intentionally bounded to:

```text
provider: Kraken
market: BTC/USD
instrument: LINEAR_PERPETUAL
provider symbol: PF_XBTUSD
depth: 25 levels per side
```

---

## Problem

Current Order Book state is held in memory and cannot support reproducible
historical research. After a disconnect, checksum failure, application restart,
or unknown update gap, the system has no durable evidence boundary from which a
trusted book can be reconstructed.

Without this foundation, future liquidity analysis could consume current provider
state, replay across an invalid sequence, use evidence acquired after its cutoff,
or silently interpolate missing updates.

---

## Scope

- Define a provider-neutral immutable Order Book evidence model in Market Data.
- Persist validated `SNAPSHOT` and `DELTA` evidence in PostgreSQL.
- Preserve `occurredAt` and `knownAt` separately.
- Assign reconstruction sequence identity and local ordering metadata.
- Preserve provider sequence metadata when the feed supplies it without inventing
  missing provider sequence numbers.
- Validate provider checksums before evidence enters trusted replay.
- Start a new reconstruction sequence after reconnect, checksum failure, invalid
  snapshot, persistence failure, or detected sequence gap.
- Persist periodic full-book Trading OS checkpoints as derived replay accelerators.
- Provide a deterministic replay foundation for a cutoff `T0` using only evidence
  with `knownAt <= T0` from one valid sequence.
- Make duplicate semantic evidence idempotent and preserve immutable conflicts.
- Version the normalized representation as `order-book-normalization-v1`.
- Bound initial acquisition to Kraken `PF_XBTUSD` at depth 25 through existing local
  configuration conventions.
- Expose minimum operational metrics for snapshots, deltas, checkpoints,
  reconnects, checksum failures, sequence resets, freshness, and persistence
  failures.

---

## Out of Scope

- Spread, depth, imbalance, microprice, liquidity concentration, absorption, or
  any other derived liquidity feature.
- `FeatureSnapshot` schema changes or `feature-projection-v2`.
- Market Intelligence liquidity analyzers or `OrderBookContext` feature contracts.
- Historical Order Book backfill from Kraken REST.
- Treating current REST or WebSocket state as historical evidence.
- Raw provider payload persistence as part of the canonical research contract.
- Catalog-wide or multi-provider Order Book acquisition.
- Kafka, ClickHouse, TimescaleDB, Redis Streams, or a new service.
- Compaction, deletion, archival, or retention automation beyond retaining V1
  canonical evidence.
- Changes to Broker, Risk, Execution, Gateway, Frontend, authentication, or
  public APIs.

---

## Acceptance Criteria

- [ ] Market Data has a provider-neutral immutable representation for normalized
      Order Book snapshots and deltas.
- [ ] Canonical evidence preserves market identity, provider provenance, depth,
      normalized levels, evidence type, sequence identity, normalization version,
      fingerprints, and trust metadata.
- [ ] `occurredAt` is the provider/source event time and `knownAt` is assigned at
      the trusted acquisition boundary; the two values are never conflated.
- [ ] Bids are canonically ordered highest-to-lowest and asks lowest-to-highest,
      using `BigDecimal` price and quantity values.
- [ ] Checksum validation is enforced before evidence is eligible for trusted
      reconstruction; failed validation cannot enter research replay.
- [ ] Reconnects, invalid snapshots, detected gaps, out-of-order updates,
      checksum failures, and persistence failures invalidate the active sequence
      and require a fresh trusted snapshot.
- [ ] Replay never applies evidence with `knownAt > T0`, crosses a known sequence
      break, uses current provider state, or fabricates missing updates.
- [ ] Replay returns an explicit unavailable or invalid result when a complete
      valid reconstruction cannot be established at `T0`.
- [ ] Periodic checkpoints are distinguishable from provider snapshots and retain
      provenance to the source evidence range.
- [ ] Duplicate semantic evidence is idempotent; conflicting immutable evidence
      is not silently overwritten.
- [ ] V1 acquisition is bounded to Kraken `PF_XBTUSD` at depth 25 and does not
      subscribe to the full market catalogue.
- [ ] Application restart resumes from a fresh provider snapshot and does not
      trust an in-memory book as durable state.
- [ ] Focused tests cover normalization, ordering, fingerprints, checksum trust,
      reconnect/reset, sequence gaps, idempotency, replay cutoff, and restart
      behavior.
- [ ] `git diff --check` passes and no unrelated service responsibility changes.

---

## Constraints

- Preserve existing Market Data ownership of provider acquisition and market
  evidence.
- Preserve Market Intelligence ownership of deterministic interpretation; do not
  move liquidity decisions into Market Data.
- Respect ADR-063 and the temporal semantics of ADR-057 and ADR-058.
- Keep canonical evidence provider-neutral; Kraken DTOs remain inside the Kraken
  infrastructure adapter.
- Fail closed when evidence trust or reconstruction continuity is uncertain.
- Keep the V1 market and depth bounded and opt-in through existing configuration
  conventions.
- Use PostgreSQL for the bounded pilot; do not introduce infrastructure without
  measured scale evidence.
- Do not mutate existing OHLC evidence, FeatureSnapshots, outcomes, or
  `feature-projection-v1`.
- Do not weaken security controls or add public exposure.
- Do not commit, push, merge, or delete unrelated worktree changes automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-063.md` - Historical Order Book evidence and replay
  semantics.
- `docs/architecture/adr/ADR-057.md` - Historical evidence and `knownAt` semantics.
- `docs/architecture/adr/ADR-058.md` - Strict replay and acquisition boundary.
- `docs/architecture/adr/ADR-060.md` - Immutable feature snapshots and provenance.
- `docs/architecture/adr/ADR-048.md` - Intelligence evidence and authority.
- `docs/architecture/adr/ADR-056.md` - Deterministic evidence and analyzer boundary.

---

## Relevant Modules

- `market-data`

Market Intelligence is a future consumer boundary and should only be changed if
required to expose the bounded replay contract. Liquidity feature implementation
belongs to Story 0112B.

---

## Validation

- Targeted Market Data unit and persistence tests for normalized snapshots,
  deltas, ordering, fingerprints, checksum trust, sequence invalidation, and
  idempotency.
- Repository/integration tests using PostgreSQL for append-only evidence,
  checkpoints, and replay cutoff selection.
- Provider adapter tests for Kraken snapshot/update mapping and reconnect behavior.
- Runtime local verification for the bounded `PF_XBTUSD` subscription, capture
  metrics, persistence growth, and restart recovery.
- Replay tests proving that `knownAt > T0` evidence and evidence after a sequence
  break are excluded.
- `git diff --check` and the repository-standard Market Data validation commands.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] ADR-063 remains the governing accepted architecture.
- [ ] Durable evidence acquisition and replay foundation implemented within scope.
- [ ] Focused and integration validation executed.
- [ ] Bounded runtime capture verified without catalog-wide activation.
- [ ] Diff reviewed in IntelliJ.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
