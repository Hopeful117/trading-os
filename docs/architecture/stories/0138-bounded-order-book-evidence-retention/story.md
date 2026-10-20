# Story 0138 - Bounded Order Book Evidence Retention and Replay Promotion

## Metadata

**ID:** `0138`  
**Title:** Bounded Order Book Evidence Retention and Replay Promotion  
**Status:** In Progress  
**Related Stories:** `0111`, `0112A`, `0112B`, `0115`  
**Related ADRs:** `ADR-057`, `ADR-058`, `ADR-060`, `ADR-063`, `ADR-065`, `ADR-066`, `ADR-067`

This artifact was created at the human engineer's request after approval of
ADR-067. It defines implementation scope but does not itself authorize code or
data-retention changes until the Story is approved.

---

## Goal

Introduce a fail-closed lifecycle for Order Book evidence that preserves frozen
research reproducibility through immutable promoted replay artifacts while making
raw evidence retention bounded by explicit dependencies and holds.

## Context

Story 0112A established immutable normalized Order Book evidence and strict replay
for the bounded Kraken `PF_XBTUSD` market at depth 25. The current evidence table
has grown to approximately 36 million rows and has no retention or release policy.
ADR-067 selects promoted replay artifacts as the reproducibility boundary and
requires conservative, dependency-aware retirement.

The current evidence must not be deleted, compacted, or reclassified as part of
this Story's preparation. Existing unknown dependencies remain protected.

## Problem

Raw snapshots and deltas can grow indefinitely even after a research dependency is
frozen. Age-based deletion is unsafe because it cannot establish whether a frozen
FeatureSnapshot, research population, historical execution replay, or integrity
investigation still depends on the evidence.

Without an immutable promotion boundary and explicit release state, the system
cannot safely distinguish evidence that may be retired from evidence required to
reproduce research truth.

## Scope

- Define an immutable, versioned replay-promotion artifact for a completed strict
  Order Book replay.
- Preserve the selected sequence, trusted starting point, cutoff, `knownAt`
  boundaries, evidence range, fingerprints, checksum/trust status, normalization
  version, and provenance.
- Add explicit evidence dependency lifecycle states:
  `PROTECTED`, `PROMOTED`, `RELEASED`, and `HOLD`.
- Associate research and replay consumers with stable semantic evidence
  dependencies rather than generated database IDs alone.
- Validate promotion as complete, same-sequence, cutoff-safe, trusted, atomic, and
  idempotent.
- Keep missing or unknown dependency metadata protected by default.
- Add conservative dependency backfill or migration metadata without deleting
  existing canonical evidence.
- Define a dry-run, observable, resumable, and idempotent retirement operation.
- Retire raw evidence only after all dependencies and holds are released and the
  sequence is no longer active.
- Add storage-pressure metrics and fail-closed capture behavior when canonical
  persistence cannot be acknowledged.
- Preserve diagnostic retention for invalidated sequences until their configured
  diagnostic period and all holds have elapsed.

## Out of Scope

- Immediate deletion, compaction, or archival of the existing Order Book table.
- Indefinite raw retention as a product requirement.
- Raw provider payload archival.
- New Kafka, ClickHouse, TimescaleDB, Redis Streams, or another service.
- Changes to Order Book normalization, checksum semantics, or replay correctness
  already governed by ADR-063 and ADR-065.
- New liquidity formulas or Market Intelligence features owned by Story 0112B.
- Changes to Risk, Broker, Execution, Gateway, Frontend, authentication, or public
  APIs.
- Automatic cleanup that bypasses dependency state, retention holds, or human
  approval policy.
- Deleting user-owned worktree changes or committing repository changes.

## Acceptance Criteria

- [ ] A completed valid replay can be promoted into an immutable, versioned artifact
      containing the required cutoff, sequence, provenance, trust, and fingerprint
      metadata.
- [ ] Promotion rejects incomplete, invalid, cross-sequence, or look-ahead replay
      results and never promotes evidence with `knownAt` after the cutoff.
- [ ] Repeating the same semantic promotion is idempotent and does not create
      conflicting artifacts.
- [ ] Research and replay dependencies have explicit lifecycle states and stable
      semantic identities.
- [ ] Missing, ambiguous, or unknown dependency metadata protects evidence rather
      than making it eligible for retirement.
- [ ] An active sequence, unresolved dependency, diagnostic investigation, or
      retention hold prevents retirement.
- [ ] Retirement is available as a dry-run before mutation and reports the exact
      evidence ranges, dependencies, holds, and promoted artifacts involved.
- [ ] Retirement is atomic, resumable, observable, and idempotent.
- [ ] Existing evidence is not deleted or reclassified by migration unless its
      dependency status is explicitly proven and the approved retirement operation
      selects it.
- [ ] Strict replay remains unchanged and can use promoted artifacts without live
      provider state or invalidated sequences.
- [ ] Storage-pressure metrics expose headroom, last successful persistence, active
      sequence, capture health, and degraded/unavailable state.
- [ ] Canonical capture fails closed when evidence cannot be durably acknowledged.
- [ ] Tests cover promotion, cutoff safety, provenance, idempotency, dependency
      release, holds, unknown metadata, dry-run cleanup, restart/resume behavior,
      and storage-pressure failure.
- [ ] `git diff --check` and applicable service validation pass without unrelated
      responsibility changes.

## Constraints

- ADR-067 is the governing retention decision.
- Market Data remains authoritative for acquisition, normalized evidence, replay,
  and retirement of its evidence.
- Market Intelligence and Trading Core must expose stable dependency boundaries;
  they must not bypass replay promotion or delete evidence directly.
- Frozen research identities and promoted artifacts are immutable.
- Cleanup must fail closed when provenance, dependency, or hold state is unknown.
- Existing approximately 36 million Order Book evidence rows remain protected until
  an approved migration and retirement procedure proves otherwise.
- Storage pressure must not weaken temporal truth, replay correctness, or auditability.
- PostgreSQL remains the bounded pilot persistence technology.
- No public API or security boundary changes are introduced.
- Human approval remains required before enabling destructive retirement in runtime.

## Relevant ADRs

- `docs/architecture/adr/ADR-067.md` - Bounded retention and replay promotion.
- `docs/architecture/adr/ADR-063.md` - Historical Order Book evidence and replay.
- `docs/architecture/adr/ADR-065.md` - Order Book epoch trust and sealed replay.
- `docs/architecture/adr/ADR-066.md` - Canonical research identity and population reproducibility.
- `docs/architecture/adr/ADR-057.md` - Historical evidence and `knownAt` semantics.
- `docs/architecture/adr/ADR-058.md` - Strict replay boundary.
- `docs/architecture/adr/ADR-060.md` - Immutable FeatureSnapshot provenance.

## Relevant Modules

- `market-data` - replay promotion, evidence dependency, retirement, and storage health.
- `market-intelligence` - research dependency and promoted replay integration where required.
- `trading-core` - historical execution replay dependency integration where required.

## Validation

- Focused Market Data unit and integration tests using PostgreSQL.
- Promotion contract tests for valid, invalid, incomplete, cross-sequence, and
  look-ahead replay inputs.
- Dependency lifecycle and cleanup dry-run tests.
- Restart/resume and idempotency tests for promotion and retirement.
- Runtime storage-pressure verification without deleting existing research data.
- Verification that active containers and existing research rows remain intact.
- Full validation suites for every modified service.
- `git diff --check` and complete diff review.

## Definition of Done

- [ ] Repository Analysis approved by human.
- [ ] Implementation Plan approved when required.
- [ ] Promotion and dependency contracts implemented within scope.
- [ ] Retention migration and retirement procedure approved before destructive activation.
- [ ] Focused and integration validation executed successfully.
- [ ] Runtime validation completed with cleanup disabled or explicitly controlled.
- [ ] Independent Code Review completed and approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
