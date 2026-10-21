# Story 0141 - Market Data Replay Performance and Reproducible Acceleration

## Metadata

**ID:** `0141`
**Title:** Market Data Replay Performance and Reproducible Acceleration
**Status:** CLOSED - HUMAN ACCEPTED
**Related Stories:** `0107B`, `0112A`, `0124`, `0140`
**Related ADRs:** `ADR-058`, `ADR-063`, `ADR-064`, `ADR-067`, `ADR-068`

This Story follows Story 0140. It addresses the measured Market Data replay
bottleneck without weakening strict historical semantics or moving replay
authority into Market Intelligence.

## Goal

Reduce the cold-path latency of strict Order Book replay while preserving the
same trusted result, cutoff semantics, sequence integrity, provenance, and
fail-closed behavior.

## Context

Story 0140 measured Research persistence reads as sub-millisecond to low-single-
digit millisecond operations, while the Market Data replay dependency remained
the dominant cost at roughly `23-41 s` on the observed cold path. The current
replay reader loads the trusted anchor and all subsequent evidence for one valid
sequence before reconstructing the book in memory.

Market Data owns historical evidence and replay. ADR-063 defines append-only
normalized evidence, sequence-specific reconstruction, and `knownAt <= cutoff`.
ADR-067 defines promoted replay artifacts, dependency protection, retention, and
provenance requirements. No optimization may make a derived acceleration layer a
silent source of truth.

## Problem

Strict replay currently pays a large repository and deserialization cost before
the book can be reconstructed. The repository contains replay accelerators and
retention foundations, but their performance, coverage, invalidation behavior,
and safe use as a derived boundary require an explicit measured implementation.

## Scope

- Establish a reproducible replay baseline by cutoff, sequence, evidence volume,
  repository time, deserialization time, assembly time, and total latency.
- Identify and compare the smallest safe acceleration options within Market Data,
  including query shape, checkpoint selection, bounded batch loading, or promoted
  replay artifacts where applicable.
- Implement an acceleration only when it produces a measured benefit and remains
  semantically equivalent to canonical evidence replay.
- Define ownership, freshness, invalidation, rebuild, degraded-state, and
  provenance behavior for any derived acceleration.
- Validate cold-cache and warm-cache behavior, sequence transitions, cutoff
  boundaries, invalidated sequences, incomplete evidence, and promotion reuse.
- Preserve explicit operational diagnostics for replay phase timings and failure
  reasons.

## Out of Scope

- Changing Market Intelligence readiness, integrity, S1 semantics, or thresholds.
- Replacing canonical Order Book evidence with a cache or TTL-only authority.
- Weakening strict replay, temporal cutoffs, sequence continuity, trust checks, or
  provider-neutral provenance.
- Deleting, compacting, or retiring evidence without the ADR-067 lifecycle rules.
- Expanding the bounded Kraken market/depth rollout.
- Implementing the unresolved Kraken Futures checksum protocol.
- New public APIs, frontend behavior, broker execution, or Trading Core changes.
- A new database, message broker, or cache platform solely for speed.

## Acceptance Criteria

- [ ] A reproducible baseline records replay query plans, evidence rows, payload
      size, deserialization time, assembly time, and total latency.
- [ ] The selected optimization has a measured benefit on representative cold and
      warm paths.
- [ ] Optimized replay returns the same status, sequence identity, cutoff-bounded
      evidence, book levels, provenance, and fingerprint as canonical replay.
- [ ] Invalidated, ambiguous, incomplete, or future-known evidence remains
      unavailable or invalid and is never served by the acceleration.
- [ ] Any derived replay artifact or checkpoint has explicit ownership, version,
      invalidation, rebuild, provenance, and dependency semantics.
- [ ] Reuse is deterministic and idempotent for the same semantic replay identity.
- [ ] Focused Market Data tests and PostgreSQL-backed replay tests pass.
- [ ] Runtime validation covers representative bounded Market Data replay paths.
- [ ] No unrelated service, API, product, or economic behavior changes.
- [ ] The implementation report records the chosen option, rejected options,
      measured benefit, residual risks, and retention implications.

## Constraints

- Preserve ADR-058, ADR-063, ADR-064, ADR-067, and ADR-068 ownership boundaries.
- Canonical normalized evidence remains authoritative and append-only.
- Strict replay must use one trusted sequence and `knownAt <= cutoff`.
- Missing provenance, invalidation state, dependency state, or rebuild state fails
  closed and must not authorize stale replay.
- Provider-specific transport behavior remains inside the Kraken adapter.
- No destructive migration or evidence retirement without explicit approval and
  the required lifecycle controls.
- Do not commit, push, merge, or discard unrelated worktree changes automatically.

## Relevant Modules

- `market-data`
- `market-intelligence` - contract and latency-consumer validation only

## Validation

- Targeted Market Data replay, persistence, retention, and sequence tests.
- PostgreSQL-backed query-plan and payload/deserialization measurements.
- Canonical-versus-optimized semantic equivalence tests.
- Cold-cache and warm-cache runtime measurements.
- `git diff --check` and relevant Market Data Maven validation.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Architecture decision or ADR update approved when required.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed within Market Data ownership.
- [ ] Relevant validation and runtime measurements pass.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.

## Closure Note

The Story is closed by explicit human instruction. No safe source or schema
optimization was retained after the measured candidate comparison. The remaining
unchecked commit item is intentionally separate because Git integration remains
human-controlled.

## Remaining Gap

Strict Market Data replay remains limited by canonical evidence loading at scale.
The next Story should design and validate checkpoint-aware replay or promoted
replay artifacts without weakening sequence integrity, `knownAt <= cutoff`,
provenance, dependency protection, invalidation, rebuild, or retention semantics.
