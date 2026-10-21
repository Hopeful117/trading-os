# Repository Analysis - Story 0141

## Repository Baseline

- Branch: `main`.
- Story 0140 is closed and identified Market Data replay as the residual
  bottleneck.
- DevLog applicability was checked. The provider returned HTTP 429 because the
  configured quota is exhausted; repository evidence is authoritative.
- The current worktree contains the uncommitted ADR-068 and Story 0140 artifact
  set. Those changes are pre-existing to Story 0141 and must remain untouched.

## Architectural Context

- ADR-058 assigns strict historical acquisition and evidence selection to Market
  Data; Market Intelligence consumes and validates the result.
- ADR-063 defines immutable normalized snapshots and deltas, sequence-specific
  reconstruction, strict `knownAt <= cutoff`, and checkpoints as derived replay
  accelerators rather than provider observations.
- ADR-064 keeps historical execution calculation in Trading Core and Market Data
  as the authority for historical market evidence.
- ADR-067 defines promoted replay artifacts, protected dependencies, explicit
  provenance, idempotent promotion, and conservative retention behavior.
- Story 0124 addresses stale active sequences and recovery. Story 0141 must not
  weaken or duplicate those lifecycle rules.

## Current Replay Path

`OrderBookReplayService.replay()` currently:

1. Calls `OrderBookEvidenceRepository.findTrusted(marketId, depth, cutoff)`.
2. Selects one eligible sequence and its latest trusted snapshot anchor.
3. Loads all trusted evidence after the anchor through the cutoff.
4. Deserializes payloads and groups evidence by sequence in memory.
5. Applies deltas in ordinal order and verifies contiguous sequence ordinals.
6. Produces the reconstructed book, provenance fingerprints, and phase timings.

`OrderBookEvidenceRepository.findTrusted()` already separates sequence selection,
anchor selection, and evidence-after-anchor loading. The current SQL returns full
normalized payloads and the replay service performs in-memory reconstruction.

`OrderBookRetentionService` already contains promotion and dependency concepts,
including persisted replay artifacts, protected dependencies, holds, dry-run
retirement, and idempotent semantic identities. Any reuse of those artifacts must
respect ADR-067 and prove that the artifact covers the requested cutoff.

## Measured Problem

Story 0140 recorded the following representative Research path:

- S1 replay/fee phase: approximately `32-36 s` cold-cache wall-clock.
- Replay calls: `54` calls, approximately `172.486 s` cumulative and `27.010 s`
  wall-clock.
- Individual replay repository lookup: approximately `6-813 ms`.
- Replay assembly was negligible compared with repository lookup.

The evidence establishes a Market Data repository/replay bottleneck, but does not
yet establish whether the dominant cost is row volume, payload size, query plan,
connection behavior, repeated requests, or sequence/checkpoint selection. Story
0141 must measure these dimensions before choosing an implementation.

## Candidate Directions

The repository supports several candidates that require measurement rather than
assumption:

- improve the existing trusted-sequence and anchor query shape;
- reduce repeated repository calls through bounded batch replay requests;
- use persisted checkpoints as derived anchors without changing canonical truth;
- promote immutable replay artifacts for stable, repeated semantic identities;
- add a narrowly scoped index or payload projection only if query evidence proves
  a durable benefit and acceptable write/storage cost.

A new cache authority, generic TTL cache, or destructive compaction is not
justified by the current evidence and would conflict with ADR-063/067 unless a
separate architecture decision explicitly governs it.

## Risks and Required Proof

- A faster query must not mix sequences or cross the requested cutoff.
- A checkpoint must retain source evidence fingerprints and normalization version.
- A promoted artifact must not hide invalidation, unresolved dependencies, or
  incomplete reconstruction.
- Any optimization must remain equivalent when the latest sequence is invalidated,
  when evidence is partial, and when a replay request is repeated.
- Storage growth and retention implications must be measured before adding durable
  derived state.

## Analysis Outcome

The Story is technically justified as a Market Data-owned replay performance
investigation and implementation. The current evidence supports measurement and
comparison, but does not yet authorize a specific projection, checkpoint policy,
index, or replay artifact lifecycle change.

Repository Analysis is ready for human approval. Implementation planning must not
begin until this analysis is explicitly approved.
