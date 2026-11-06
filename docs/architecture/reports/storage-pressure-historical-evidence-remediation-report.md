# Trading OS - Storage Pressure & Historical Evidence Remediation Report

**Date:** 2026-11-06  
**Status:** `OFFLINE_REMEDIATION_COMPLETE_OPERATIONAL_GATES_OPEN`  
**Scope:** targeted Market Intelligence provenance/cutoff remediation and read-only storage response planning

## Executive Result

The historical evidence contract was repaired offline without modifying runtime
data. Market Intelligence now preserves `fetchedAt` and the complete existing
context evidence identity in persisted context summaries. Dataset construction
now rejects missing or post-cutoff evidence availability explicitly.

The storage incident remains operationally unresolved because cleanup was not
authorized or executed. The Order Book population, PostgreSQL volumes, running
containers, and capture configuration were not modified.

No real historical sample has been proven eligible by this work. Bundle v2
remains a negative regression reference.

## Storage

### Current State

The read-only recheck confirms:

```text
Filesystem:       449G total, 423G used, 3.0G free, 100%
Docker build cache: 24.4G total, 16.25G reclaimable by docker system df
Docker images:    13.66G total, 5.304G reclaimable
Docker volumes:   96.72G total, 442.2M reclaimable
Containers:       25 running; no active application build process observed
```

The system remains at risk of PostgreSQL write failures, incomplete evidence
persistence, failed container operations, and service instability. No emergency
database cleanup was attempted.

### Proposed Recovery Operation

Candidate target: Docker build cache only.

```bash
docker builder prune --force
```

Expected recovery is up to approximately 16.25 GB based on the conservative
`docker system df` reclaimable value. `docker buildx du` reports additional
shared cache accounting; the exact recovered amount must be measured after an
approved operation rather than assumed.

Risks:

- cached build layers are deleted and may need to be rebuilt;
- concurrent builds could race with cache removal;
- cache deletion is not reversible except by rebuilding;
- this command must not be substituted with volume pruning or PostgreSQL file
  manipulation.

No active build process was observed during the recheck. Running containers and
named volumes are not targeted by this command. The approval-time verification
procedure is:

1. Confirm no build is active and confirm the target builder/context.
2. Execute the exact command above only after explicit human approval.
3. Re-run `df -h /` and `docker system df`.
4. Confirm all named volumes and containers are unchanged.
5. Confirm PostgreSQL accepts a read-only connection and that no application
   capture job was started by the cleanup.

If the full conservative estimate is recovered, free space would be roughly
19.25 GB, or about 4.3% of the 449 GB filesystem. This improves availability
but does not satisfy the 10% headroom gate. Cleanup remains a human decision and
was not executed.

## Provenance

### Confirmed Root Cause

Market Data already persisted OHLC evidence with `fetched_at`, `captured_at`, and
`known_at`. The Market Intelligence historical client received temporal fields,
but persisted `ConsolidatedIntelligence.contextSections` commonly retained only
`sourceOccurredAt`. The required `fetchedAt` was therefore absent at export
time, and the exporter correctly failed closed.

### Remediation

The current remediation changes:

- retain `ContextProvenance.fetchedAt` in both consolidation paths;
- retain source reference, source/provider version, normalization version,
  evidence policy, evidence fingerprint, observation count, and evaluation cutoff
  in `ContextSectionSummary`;
- preserve a compatibility constructor so older persisted summaries without the
  new fields deserialize as incomplete rather than fabricating values;
- derive the export evidence availability from persisted execution context;
- retain strict exclusion when any required context timestamp is missing;
- reject evidence availability after the snapshot evaluation cutoff;
- expose `featureEvidenceKnownAt` separately from `featureSnapshotCreatedAt` in
  JSONL output.

No exporter relaxation was introduced. No old context, analysis, or feature
snapshot was rewritten or backfilled.

### Contract

For strict observed research:

```text
knownAt <= evaluationCutoff
```

`sourceOccurredAt` is not substituted for `fetchedAt`. Missing temporal evidence
remains `FEATURE_EVIDENCE_UNAVAILABLE`.

## Snapshot Timing

### Semantics

`ResearchLifecycleService` submits a historical execution at an aligned cutoff,
then projects completed executions later. `FeatureSnapshot.createdAt` is the
projection persistence time; `FeatureSnapshot.evaluationCutoff` is the temporal
boundary of the evidence. These timestamps must remain distinct.

The remediation does not backdate `createdAt`, reject every retrospective
projection, or treat retrospective creation as contemporaneous observation. Such
a rule would be a new domain policy. Eligibility is instead based on persisted
evidence availability and artifact cutoff validation.

### Regression Coverage

A projection test now verifies that a snapshot projected one hour after its
cutoff retains the later `createdAt` while preserving the original cutoff. This
prevents timestamp falsification and makes retrospective semantics explicit.

The dataset builder tests verify:

- complete evidence at or before cutoff passes;
- missing evidence fails closed;
- post-cutoff evidence fails closed;
- existing feature snapshot and frozen identity behavior remains deterministic.

## Dataset Eligibility

### Offline Results

The valid synthetic fixture with complete temporal evidence is eligible.

The following synthetic cases are excluded deterministically:

- missing `fetchedAt`/evidence availability;
- `knownAt` after the evaluation cutoff.

The existing bundle v2 remains unchanged as a negative regression reference:

```text
Historical samples: 96
Eligible:            0
Excluded:           96
Reason:              FEATURE_EVIDENCE_UNAVAILABLE
```

No real historical sample was proven eligible. No production export, research
job, backfill, or external market call was executed.

## Independent Review

An independent read-only review identified:

- a real missing cutoff check in the dataset builder;
- a real loss of context-level source identity;
- insufficient end-to-end tests for persisted execution summaries;
- a residual risk that aggregate context timestamps do not independently prove
  each feature artifact's exact evidence boundary;
- a policy question around retrospective snapshot creation time.

The first two defects were addressed. The retrospective timestamp policy was
not invented or changed; the existing architecture treats `createdAt` as
projection time and the report/tests preserve that distinction. The remaining
artifact-to-source proof risk requires a bounded replay/reconstruction decision,
not a permissive exporter change.

The reviewer also noted that Draft Story 0142 lists Market Intelligence changes
out of scope. The current user-authorized remediation explicitly targets the
confirmed Market Intelligence provenance defect; this scope deviation must be
recognized and approved before integration. No Order Book retention or replay
implementation was added.

## Validation

Executed:

- read-only filesystem, Docker, container, cache, and runtime diagnostics;
- targeted Market Intelligence tests: 37 passed, 0 failed;
- full Market Intelligence Maven suite: 877 passed, 0 failed, 0 skipped;
- `git diff --check`: passed;
- independent read-only code review.

The Maven suite used H2/in-memory integration fixtures and did not start the
production stack. Some tests emitted expected warnings for absent service
discovery/JWT test dependencies; no test failed.

Not executed:

- Docker cleanup;
- production deployment or restart;
- live Market Data capture or provider calls;
- PostgreSQL production mutation or backfill;
- bounded replay against the 85 GB Order Book population;
- real bundle v2 re-export;
- LLM, broker, or trade operation.

## Next Decision Gates

The following gates remain open:

1. **Storage cleanup approval:** authorize or reject `docker builder prune --force`.
2. **Runtime deployment approval:** deploy the provenance changes only after
   human review of the existing dirty worktree and scope conflict with Story
   0142.
3. **Historical reconstruction approval:** decide whether selected old
   executions may be reconstructed from preserved OHLC evidence without changing
   frozen identities.
4. **Bounded replay approval:** approve a read-only bounded replay/classification
   path for selected sealed Order Book sequences under ADR-063/ADR-067 and Story
   0142 safeguards.
5. **Benchmark Gate B continuation:** keep blocked until a real, provenance-valid
   sample population is produced; synthetic eligibility is not real evidence.

None of these gates was executed automatically.
