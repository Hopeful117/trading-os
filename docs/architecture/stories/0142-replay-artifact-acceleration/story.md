# Story 0142 - Reproducible Order Book Replay Acceleration

## Metadata

**ID:** `0142`
**Title:** Reproducible Order Book Replay Acceleration
**Status:** Implementation complete - human approval pending
**Related Stories:** `0141`, `0112A`, `0124`
**Related ADRs:** `ADR-058`, `ADR-063`, `ADR-064`, `ADR-067`, `ADR-069`

## Goal

Design, measure, and, only when justified, implement a reproducible Market Data
replay accelerator that materially reduces strict Order Book replay latency while
preserving canonical evidence, sequence integrity, temporal cutoffs, provenance,
fail-closed behavior, and retention safety.

## Context

Story 0141 rejected simple SQL index alternatives. The remaining candidates are
checkpoint-aware replay and immutable promoted replay artifacts. ADR-069 defines
the boundary for comparing and implementing those candidates.

## Scope

- Establish a representative baseline for replay latency, evidence volume,
  checkpoint distance, payload/deserialization cost, and repeated semantic
  requests.
- Compare checkpoint-aware replay and promoted replay artifacts against canonical
  replay.
- Define ownership, identity, provenance, invalidation, rebuild, degraded-state,
  dependency, and retention behavior for the selected candidate.
- Implement the smallest candidate only after semantic equivalence and measured
  benefit are demonstrated.
- Validate sequence recovery, invalidation, cutoff boundaries, incomplete data,
  repeated requests, and rebuild failure behavior.

## Out of Scope

- Replacing canonical evidence or changing strict replay truth.
- Generic TTL-only caching.
- Deleting, compacting, or retiring evidence without ADR-067 lifecycle controls.
- Changes to Market Intelligence readiness semantics or public APIs.
- New database, message broker, or cache platform.
- Kraken checksum protocol changes or expansion beyond the bounded rollout.

## Acceptance Criteria

- [x] A reproducible canonical replay baseline is recorded.
- [ ] Checkpoint and promoted-artifact candidates are compared on representative
       cold, warm, repeated, invalidated, and cutoff-bounded paths.
- [x] The selected candidate has a material measured benefit and a documented
       rejection rationale exists for the alternative.
- [x] Optimized and canonical replay produce equivalent status, sequence identity,
       book levels, cutoff-bounded provenance, and fingerprints.
- [x] Ownership, identity, invalidation, rebuild, degraded-state, dependency, and
       retention semantics are explicit and fail closed.
- [x] Sequence recovery and invalidation cannot serve stale derived state.
- [x] Focused Market Data and PostgreSQL-backed tests pass.
- [x] Runtime validation and `git diff --check` pass.
- [x] Any required ADR update is accepted before architectural implementation.
- [x] The implementation report records measured benefit, residual risks, and the
       remaining gap.

### Acceptance Status Note

The existing checkpoint path provides a measurable reduction. The artifact-first
read path is implemented behind the optional `semanticIdentity` replay parameter,
with strict validation and canonical fallback. PostgreSQL-backed Testcontainers
 validation now covers promoted artifact reads, corruption fallback, invalidation,
 supersession, retention behavior, full payload semantic verification, complete
 response equivalence, and concurrent execution of the replay and invalidation
 paths. The controlled artifact benchmark uses 10001 evidence rows; production-scale
 artifact reuse remains unmeasured.

## Constraints

- Preserve ADR-058, ADR-063, ADR-064, ADR-067, and ADR-069.
- Keep Market Data as the owner of historical evidence and replay acceleration.
- Require `knownAt <= evaluationCutoff` and one valid trusted sequence.
- Never serve unknown, stale, invalidated, incomplete, or unverifiable derived
  state as trusted replay.
- Do not perform destructive retention actions in this Story unless explicitly
  approved by the applicable lifecycle decision.
- Do not commit, push, merge, or discard unrelated work automatically.

## Relevant Modules

- `market-data`
- `market-intelligence` only for replay contract compatibility validation

## Validation

- Targeted replay, persistence, retention, sequence, and lifecycle tests.
- PostgreSQL-backed canonical-versus-optimized equivalence tests.
- Cold, warm, repeated, invalidated, and rebuild-failure runtime measurements.
- Relevant Market Data Maven validation and `git diff --check`.

## Definition of Done

- [x] Repository Analysis approved.
- [x] ADR-069 accepted by the human engineer on 2026-10-21.
- [x] Implementation Plan approved when required.
- [x] Implementation completed within Market Data ownership.
- [x] Semantic equivalence and lifecycle validation pass for the promoted-artifact
      acceleration path in PostgreSQL-backed tests.
- [ ] Code Review approved.
- [x] Engineering Report completed.
- [ ] Human commit created.
