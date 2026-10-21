# Repository Analysis - Story 0142

## Repository Baseline

- Branch: `story/0142-replay-artifact-acceleration`.
- Stories 0140 and 0141 are committed and merged into local `main`.
- Story 0141 measured the replay bottleneck and retained no source optimization.
- DevLog applicability was checked but the provider returned HTTP 429 because the
  configured quota is exhausted. Repository evidence is authoritative.

## Current Evidence

The local Market Data database contains approximately:

- `49.5 M` Order Book evidence rows;
- `31 GB` database size;
- `170` capture sequences;
- zero promoted replay artifacts in the measured dataset.

The current replay path selects one eligible sequence, chooses the latest trusted
snapshot/checkpoint at the cutoff, loads subsequent trusted evidence, and rebuilds
the book in memory. Story 0141 measured approximately `242 ms` for the bounded
delta read after a checkpoint and approximately `290 ms` for a cold sequence
selection in the observed environment.

## Existing Repository Capabilities

- `OrderBookReplayService` reconstructs one trusted sequence and emits a
  provider-neutral `OrderBookReplayResult` with source fingerprints and phase
  timings.
- `OrderBookEvidenceRepository` already selects a cutoff-valid sequence, chooses a
  trusted snapshot/checkpoint, and loads ordered evidence after the anchor.
- `OrderBookEvidenceCaptureService` creates periodic derived checkpoints while
  preserving provider snapshots and deltas as canonical evidence.
- `OrderBookRetentionService` already exposes immutable replay artifact promotion,
  dependency protection/release, retention holds, dry-run retirement, and
  idempotent semantic identity handling.
- ADR-067 requires replay promotion and dependency metadata before evidence
  retirement; unknown dependencies remain protected.

## Architectural Constraints

- ADR-063 makes canonical normalized snapshots and deltas the authoritative source
  for strict replay and requires one valid reconstruction sequence.
- ADR-058 requires strict historical acquisition to remain provider-neutral and
  cutoff-bounded; live state is never a replay fallback.
- ADR-064 keeps historical economic calculation outside Market Data while Market
  Data remains authoritative for historical market evidence.
- ADR-069 is proposed to define the acceleration boundary but must be accepted
  before any material ownership, retention, or provenance change.

## Candidate Comparison Questions

### Checkpoint-Aware Replay

- Are existing checkpoints frequent and complete enough to reduce evidence scans?
- Can checkpoint selection remain sequence- and cutoff-safe under invalidation?
- Can checkpoint provenance be verified without rescanning the full source range?
- What rebuild behavior is required after a persistence or sequence failure?

### Promoted Replay Artifacts

- Which semantic replay identities repeat in real Research workloads?
- Can artifacts be reused without silently serving a stale cutoff or invalidated
  sequence?
- Can `matchesPersistedEvidence` and dependency protection prove the artifact's
  source range without recreating the original cost?
- What protects artifacts when raw evidence is released under ADR-067?

## Risks

- Derived state can become a second source of truth if provenance is incomplete.
- Checkpoints can cross a sequence boundary if invalidation is not atomic.
- Promoted artifacts can become stale if dependency release is implicit.
- Rebuild failures can be hidden if degraded state is represented as available.
- Artifact storage can grow faster than canonical evidence if semantic identities
  are not bounded.

## Analysis Outcome

Story 0142 is justified as a Market Data-owned design and implementation
investigation. The repository already contains the foundational checkpoint and
promotion capabilities, but the evidence does not yet justify selecting one. The
next stage is to obtain human approval of this Repository Analysis, then prepare a
plan that defines the measurements and candidate decision gates.
