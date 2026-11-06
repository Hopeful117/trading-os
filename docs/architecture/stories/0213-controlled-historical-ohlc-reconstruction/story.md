# Story 0213 - Controlled Historical OHLC Reconstruction

## Metadata

**ID:** `0213`  
**Title:** Controlled Historical OHLC Reconstruction  
**Status:** Draft  
**Priority:** P1 - Trading Intelligence  
**Predecessors:** `0107A`, `0107B`, `0111`, Gate C real historical OHLC validation  
**Related ADRs:** `ADR-057`, `ADR-058`, `ADR-060`, `ADR-063`, `ADR-067`

---

## Goal

Produce one real, reproducible, strictly eligible historical research sample
from existing persisted OHLC evidence without collecting new data, weakening
`STRICT_OBSERVED`, replaying Order Book evidence, or creating a parallel
research pipeline.

The first proof scope is one market, one cutoff, one timeframe, and one
canonical outcome horizon. The result must remain visibly retrospective: the
artifact creation time is the actual creation time and is never treated as the
historical observation time.

---

## Context

Gate C verified real persisted OHLC evidence for market
`8b336ec3-1557-4432-b052-ae569e041dac`, including identity, source, origin,
`fetchedAt`, `capturedAt`, `knownAt`, normalization version, and fingerprints.
The Gate C cutoff `2026-11-06T16:45:00Z` had only one future 15-minute candle,
which is insufficient for the existing canonical `PT1H` outcome horizon.

An additional bounded read-only preflight during this design verified a suitable
proof candidate at:

```text
market: 8b336ec3-1557-4432-b052-ae569e041dac
timeframe: FIFTEEN_MINUTES
evaluationCutoff: 2026-11-06T15:45:00Z
outcome horizon: PT1H
feature inputs: 20 or more strict observed rows
future inputs: four contiguous 15-minute rows through 16:45Z
```

The preflight returned 20 strict feature rows ending at 15:30Z and four future
rows beginning at 15:45Z. This is design evidence only; implementation must
repeat the same query and record the complete selected identity set before any
Market Intelligence write.

The selection is bound by an ordered observation-reference set and an aggregate
selection fingerprint. The same set must be asserted when context is acquired;
an `asOf + limit` response that returns a different set is rejected.

The current repository already contains the relevant contracts and services:

- `HistoricalOhlcEvidence` and `HistoricalOhlcHistoryService` own bounded
  strict historical reads in Market Data.
- `HistoricalOhlcEvidenceSource` and
  `TrendContextRoleHistoryContextContributor` acquire strict historical
  context through the existing provider-neutral client.
- `AnalysisExecutionService` owns idempotent execution registration and
  lifecycle persistence.
- `FeatureSnapshotProjectionService` and `FeatureProjectionV1` own deterministic
  feature projection and snapshot persistence.
- `MarketOutcomeEvaluationService`,
  `HistoricalOhlcReferenceEvidenceSource`,
  `HistoricalOhlcFutureEvidenceSource`, and `MarketOutcomeCalculator` own
  independent future outcome construction.
- `ResearchDatasetExportService` and `ResearchDatasetBuilder` own canonical
  strict export eligibility.

The previous provenance remediation is complete and must be reused, not
duplicated. Existing Order Book evidence remains protected and is not part of
this Story.

---

## Problem

The existing lifecycle can persist analysis executions, feature snapshots, and
future outcomes, but it has no explicit controlled reconstruction origin or
single-sample execution boundary. Gate C evidence therefore cannot currently be
carried through the canonical Market Intelligence artifact lifecycle without
either:

- creating an ordinary-looking analysis that could be mistaken for a
  contemporaneous observation; or
- building an offline exporter that duplicates feature and eligibility logic.

Neither is acceptable. The missing capability is a bounded, explicitly marked
historical reconstruction execution that reuses the existing deterministic
services and canonical exporter.

---

## Product Objective

Enable an operator-approved, one-shot reconstruction of one historical research
sample whose source evidence, feature artifacts, outcome artifacts, and export
eligibility can be independently audited and reproduced.

The mechanism is for research evidence only. It does not create a trading
opportunity, market thesis, risk decision, execution intent, broker operation,
or live analysis result.

---

## Scope

- Add an explicit `HISTORICAL_RECONSTRUCTION` origin to the existing analysis
  execution provenance contract, with backward-compatible contemporary defaults
  for legacy executions.
- Preserve `fetchedAt`, `capturedAt`, and `knownAt` distinctly through the
  historical OHLC response, `OhlcResponse`, `ContextProvenance`, and
  `ContextSectionSummary`; the current mapping that places `knownAt` into
  `fetchedAt` is not sufficient for this Story.
- Add a controlled application-level reconstruction command/use case inside
  `market-intelligence`; do not create a new service or public endpoint.
- Reuse the existing strict historical OHLC acquisition and context
  contributors.
- Reuse the existing deterministic capability execution and artifact
  persistence path.
- Freeze the strict reconstruction capability allowlist to the complete
  deterministic dependency closure: `trend-context-analysis`,
  `market-structure-analysis`, `ohlc-range-analysis`, and
  `ohlc-momentum-analysis`. The context allowlist remains historical OHLC and
  Trend Context only. Any strategy-plan drift that requests current snapshot,
  spread, news, order-book, live-provider evidence, or an unlisted capability
  fails before execution.
- Reuse `FeatureSnapshotProjectionService` and `FeatureProjectionV1`.
- Reuse `MarketOutcomeEvaluationService` and `market-outcome-v1`.
- Reuse `ResearchDatasetBuilder` and `ResearchDatasetExportService` for final
  eligibility.
- Mark the resulting lineage as retrospective through the execution origin and
  exported lineage metadata, while preserving actual `createdAt` and
  `computedAt` values.
- Enforce one market, one cutoff, one `PT1H` horizon, one reconstruction
  execution, and one export sample for the first run.
- Add deterministic tests for temporal rejection, idempotency, lifecycle
  lineage, future-data separation, and canonical export.

---

## Out of Scope

- New OHLC collection, provider integration, backfill, or historical rewriting.
- Any modification of `historical_ohlc_evidence` or its timestamps.
- Order Book replay, promotion, retention, cleanup, or dependency release.
- New research algorithms, feature definitions, outcome definitions, or horizons.
- A separate feature projection, outcome calculator, or eligibility rule.
- LLM calls, broker calls, trade execution, Market Thesis mutation, or risk
  decisions.
- Bulk reconstruction, scheduling, automatic retries, multi-market population,
  or benchmark execution.
- Public or Gateway-exposed reconstruction endpoints.
- Rewriting frozen samples, snapshots, or dataset bundles.
- Migration of the 85 GB Market Data database.
- Commit, push, merge, or worktree cleanup.

---

## Selected Reconstruction Approach

### Decision: controlled persisted reconstruction

Use the existing Market Intelligence execution lifecycle and persist only the
bounded derived artifacts required by the canonical exporter. This is preferred
over a transient offline pipeline because `FeatureSnapshotProjectionService`,
`MarketOutcomeEvaluationService`, and `ResearchDatasetExportService` already
define the authoritative feature, outcome, identity, idempotency, and
eligibility behavior. Reimplementing those rules offline would create a weaker
parallel contract.

The reconstruction command must:

1. Validate the fixed market, cutoff, timeframe, horizon, evidence policy, and
   resource budget.
2. Acquire only Market Data historical evidence through the existing internal
   provider-neutral contract.
3. Register one idempotent `AnalysisExecution` with explicit
   `HISTORICAL_RECONSTRUCTION` provenance.
4. Run the existing deterministic strict analysis path and persist its bounded
   artifacts.
5. Require an available `market-structure-v2` artifact produced by the same
   reconstruction, then project exactly one `FeatureSnapshot` using the
   existing projection service. The canonical projection contract remains
   unchanged for non-reconstruction callers.
6. Evaluate exactly one `ObservedMarketOutcome` with `market-outcome-v1` using
   only future OHLC rows after the cutoff.
7. Build and export exactly one sample through the existing strict dataset
   builder/exporter.
8. Return an explicit eligible or rejected result with all source and derived
   identities.

The command is offline/operator initiated and disabled unless explicitly
invoked. It must not be implemented as a scheduler or public HTTP endpoint.
Invocation requires the dedicated reconstruction command, an explicit
`--confirm-historical-reconstruction` flag, and a non-empty human approval
reference. The command records the local operator identity, approval reference,
request fingerprint, and terminal result in structured audit logging. Ordinary
user or `TRADING_CORE` requests cannot request reconstruction. A future internal
trigger would require a dedicated service authority and must not reuse the
ordinary analysis authority.

The command accepts only a run identifier, never an arbitrary output path. The
output root is a preconfigured, non-user-writable application setting; path
traversal, symlinks, existing directories, and output outside that root are
rejected. The local operator principal is obtained from the trusted process
launcher and is recorded with the human approval reference. This is an
operator-controlled offline command, not a replacement for service JWT
authorization.

### Offline versus persisted comparison

| Option | Assessment |
| --- | --- |
| Offline transient artifacts | Avoids writes but would need to recreate execution/artifact lineage or bypass canonical persistence and export contracts. Rejected for the first proof. |
| Controlled persisted reconstruction | Reuses canonical deterministic services and exporter; requires explicit origin, idempotency, bounded writes, and operator approval. Selected. |

The selected approach writes only Market Intelligence derived artifacts. It
never writes Market Data evidence and never touches Order Book storage.

---

## Temporal Invariants

- Every feature OHLC observation must satisfy `knownAt <= evaluationCutoff`.
- `closeTime`, `sourceOccurredAt`, and `fetchedAt` must satisfy all existing
  strict validators; none may be replaced by another timestamp.
- Future outcome observations are queried only for
  `[evaluationCutoff, evaluationCutoff + PT1H]` under the existing captured
  range contract and are never included in context or feature artifacts.
- `FeatureSnapshot.createdAt` remains the actual persistence time.
- Outcome `computedAt` remains the actual calculation time.
- Reconstruction origin and execution completion time identify retrospective
  creation; neither is substituted for historical availability.
- Missing, null, post-cutoff, synthetic, unclosed, mixed-version, or
  conflicting evidence fails closed.
- No feature fingerprint includes future outcome data.

The existing `HistoricalOhlcEvidenceSource`,
`StrictObservedOhlcEvidenceValidator`, `HistoricalOhlcContextContributor`, and
`HistoricalOhlcFutureEvidenceSource` remain the enforcement points. The Story
adds orchestration and lineage; it does not relax those rules.

---

## Artifact Lifecycle And Identity

1. **Immutable source evidence**: existing Market Data OHLC rows, referenced by
   observation IDs and fingerprints. Never mutated.
2. **Historical context**: strict context sections containing source identity,
   evidence fingerprint, observation count, normalization version, policy,
   cutoff, and real evidence timestamps.
3. **Reconstructed execution**: one `AnalysisExecution` with explicit origin,
   fixed cutoff, strict policy, and an idempotency key of the form:
   `historical-reconstruction:v1:<marketId>:<cutoff>:<projection>:<horizon>`.
4. **Feature snapshot**: existing `FeatureSnapshot` linked to the execution by
   `analysisExecutionId`; its semantic fingerprint remains independent of UUID,
   `createdAt`, and reconstruction wall-clock time.
5. **Future outcome**: existing `ObservedMarketOutcome` linked to the same
   execution, using the existing reference/future evidence fingerprints and
   `market-outcome-v1` fingerprint contract.
6. **Research sample**: existing `ResearchSample` selected by the existing
   `ResearchPopulationSpec`, with feature and outcome fingerprints selected
   explicitly.

The smallest contract extension is to carry `executionOrigin` and a
`reconstructionRequestFingerprint` in `AnalysisExecutionProvenance` and through
the serialized execution payload. Existing snapshots and outcomes already
retain `analysisExecutionId`; the exporter can resolve origin and reconstruction
completion time through that execution instead of adding new tables or
duplicating origin columns. The exported lineage must include
`executionOrigin`, `reconstructionExecutionId`, and the recorded source-evidence
fingerprint set for reconstructed samples.

The request fingerprint also includes the ordered feature and future observation
reference sets selected by preflight. The execution repository must provide an
atomic compare-or-conflict operation for this fingerprint and idempotency key;
the current key-only reuse operation is insufficient.

The historical OHLC adapter must also carry all three acquisition timestamps
without renaming them. The future outcome adapter must validate non-null source
identity, observation fingerprint, `fetchedAt`, `capturedAt`, `knownAt`, and
normalization for every future row before passing rows to the existing
calculator. The aggregate future evidence fingerprint remains the canonical
outcome identity; the bounded source fingerprint set is retained in the
reconstruction audit lineage.

The future row references are retained in the bounded reconstruction audit event
and included in the request fingerprint; the outcome domain keeps its existing
aggregate `futureEvidenceFingerprint` as the stable semantic identity. No new
large evidence table is introduced.

The same complete provenance is required for the `market-structure-v2` input
lineage. Each structure candle/reference must retain its Market Data observation
ID, observation fingerprint, source identity, origin, normalization version,
`fetchedAt`, `capturedAt`, and `knownAt`. A structure artifact is not eligible
for historical projection when any of these fields is missing or when any
`knownAt` exceeds the evaluation cutoff.

Legacy executions with no origin field deserialize as
`CONTEMPORARY_ANALYSIS`; they are never reclassified as reconstructed.

Repeated execution with the same idempotency key returns the existing execution
and must not create a second snapshot, outcome, or sample. The command compares
the stored request fingerprint, origin, cutoff, policy, projection, horizon,
allowlist, and selected source fingerprint set before returning an existing
execution. A conflicting request fails closed. An expired or failed execution
is not silently reused for a new attempt; a human-approved rerun receives a new
attempt key but must first prove that no successful semantic artifacts already
exist.

---

## Operational Bounds

- Markets: exactly 1.
- Evaluation cutoffs: exactly 1.
- Outcome horizons: exactly 1, existing `PT1H` only for the first proof.
- Feature policy: exactly `STRICT_OBSERVED`.
- Trigger timeframe: existing `FIFTEEN_MINUTES` research contract.
- Trend role retrieval: reuse current per-role limits, starting at configured
  requested candles and never exceeding the existing 720-candle cap.
- Total reconstruction executions: 1 per invocation.
- Feature snapshots: at most 1 new snapshot.
- Outcomes: at most 1 new outcome.
- Export samples: at most 1.
- Retries: no automatic retry loop; one explicit rerun is allowed only with a
  new attempt key after inspection of a failed terminal result and proof that
  no successful semantic artifacts already exist.
- Concurrency: 1 reconstruction at a time per Market Intelligence instance;
  cross-instance execution requires the existing idempotency boundary.
- Database writes: only the one execution lifecycle, its bounded deterministic
  artifacts, one feature snapshot, one outcome, and the associated audit state.
  No writes to Market Data.
- Temporary disk: no bulk export; one small canonical sample and bounded logs,
  with a configured 100 MB hard limit.
- Query/read duration: fail closed after 30 seconds per Market Data request and
  5 minutes total for the reconstruction command.
- Capability artifacts: exactly the four strict allowlisted deterministic
  capabilities in the dependency closure, including
  `market-structure-analysis`, at most one persisted result per capability, and
  no unlisted artifact. Exceeding this budget fails closed.
- No scheduler, collection trigger, Order Book request, or LLM call.

At the five-minute deadline, the command invokes the existing cancellation
transition for a cancellable execution, records a terminal timeout result, and
does not project, evaluate, or export. If cancellation races with completion,
the command verifies the persisted terminal state and still refuses downstream
steps unless all preconditions are satisfied.

The preflight must reject the candidate before any write if the feature role
history or the complete PT1H future range is unavailable.

---

## Failure Scenarios

| Failure | Required behavior |
| --- | --- |
| Missing OHLC evidence | Reject before execution persistence with explicit missing-evidence status. |
| Missing provenance | Reject before context construction; never synthesize timestamps. |
| Missing or degraded `market-structure-v2` | Reject before feature projection; do not create a historical snapshot with an unavailable structure group. |
| `knownAt > cutoff` | Reject the evidence and the candidate; do not filter it silently into a valid result. |
| Unsupported timeframe or horizon | Reject input; do not create a new outcome contract. |
| Incomplete feature role window | Reject before projection and report the missing role/interval. |
| Missing future observation or gap | Persist no eligible outcome; return explicit incomplete/unavailable result and do not export. |
| Duplicate reconstruction | Return the existing semantic execution/result through idempotency. |
| Conflicting identity or fingerprint | Fail closed and preserve all existing artifacts. |
| Projection validation failure | Mark execution failed; do not create a snapshot. |
| Outcome calculation failure | Preserve valid execution/snapshot only as a non-exportable partial result; never fabricate an outcome. |
| Export rejection | Preserve diagnostic lineage, classify the sample with the canonical exclusion reason, and do not claim success. |
| Persistence failure or timeout | Fail closed, record bounded diagnostics, and require explicit inspection before rerun. |
| Storage budget exceeded | Stop before the next write; do not prune or alter Market Data. |

---

## Acceptance Criteria

- [ ] **AC-01 - Historical evidence selection:** one bounded deterministic
      query/call selects existing OHLC evidence for one market, timeframe and
      cutoff using existing Market Data contracts.
- [ ] **AC-02 - Temporal provenance:** every selected feature input preserves
      its real observation ID, fingerprint, source, origin, normalization
      version, `fetchedAt`, `capturedAt`, and `knownAt`; the same provenance is
      retained by every `market-structure-v2` input reference.
- [ ] **AC-03 - Strict cutoff:** any feature evidence with
      `knownAt > evaluationCutoff` is rejected.
- [ ] **AC-04 - Missing provenance:** missing mandatory provenance is rejected;
      no timestamp is fabricated.
- [ ] **AC-05 - Retrospective semantics:** reconstructed execution, snapshot,
      outcome, and export lineage are explicitly marked and retain actual
      creation/calculation timestamps.
- [ ] **AC-06 - Feature projection:** existing deterministic context,
      capability, artifact, and `FeatureProjectionV1` logic is reused without a
      parallel projection rule. The reconstruction precondition rejects missing
      or degraded `market-structure-v2` before calling the unchanged canonical
      projection service.
- [ ] **AC-07 - Outcome independence:** future observations cannot enter the
      feature context, and tests prove the separation.
- [ ] **AC-08 - Canonical eligibility:** final status is produced by the
      existing `ResearchDatasetBuilder` and strict exporter.
- [ ] **AC-09 - Reproducibility:** repeating the same reconstruction from the
      same evidence and versions yields equivalent context, feature, outcome,
      sample, and dataset fingerprints.
- [ ] **AC-10 - Idempotency:** repeated invocation with the same semantic key
      does not create uncontrolled duplicate artifacts.
- [ ] **AC-10A - Dependency closure:** the reconstruction validates exactly
      `trend-context-analysis`, `market-structure-analysis`,
      `ohlc-range-analysis`, and `ohlc-momentum-analysis`; no hidden producer or
      unlisted capability is accepted.
- [ ] **AC-10B - Structure provenance:** a structure artifact is rejected when
      any contributing OHLC observation lacks identity, fingerprint, captured
      origin, normalization version, `fetchedAt`, `capturedAt`, or `knownAt`, or
      when `knownAt > evaluationCutoff`.
- [ ] **AC-11 - Operational bounds:** the first run is exactly one market, one
      cutoff, one `PT1H` horizon, and one sample.
- [ ] **AC-12 - Existing data preservation:** no source evidence or frozen
      research artifact is rewritten.
- [ ] **AC-13 - Failure behavior:** missing, inconsistent, post-cutoff, or
      incomplete evidence produces an explicit fail-closed result.
- [ ] **AC-14 - Benchmark isolation:** no real LLM call or benchmark execution
      occurs.
- [ ] **AC-15 - Storage safety:** no bulk copy, Order Book replay, unbounded
      scan, migration, or uncontrolled storage growth occurs.

---

## Validation Strategy

- Unit tests for reconstruction input validation, origin propagation,
  idempotency keys, cutoff rejection, missing provenance, and failure states.
- Integration tests using isolated PostgreSQL fixtures for execution,
  deterministic artifact, snapshot, and outcome persistence.
- Contract tests proving the historical Market Data request contains the exact
  market, interval, cutoff, limit, and strict policy.
- Feature projection tests proving `createdAt` is later than the cutoff without
  changing the cutoff or feature fingerprint.
- Outcome tests proving only future rows are used and that incomplete PT1H
  coverage is rejected.
- Export tests proving the existing strict builder selects exactly one eligible
  sample and emits reconstruction lineage.
- Market Structure tests proving the approved dependency closure, complete OHLC
  provenance propagation, cutoff rejection, conflicting duplicate rejection,
  and rejection of missing/degraded structure before projection.
- Repeat-run tests proving stable semantic fingerprints and no duplicates.
- One opt-in local runtime proof against existing data only after explicit
  implementation and runtime approvals; no provider, LLM, broker, or collection
  call.
- `git diff --check` and affected Market Intelligence tests.

The current exporter target is `research-dataset-bundle-v1`. The phrase
"bundle v2" refers only to the existing frozen negative reference fixture and
is not changed by this Story. Reconstruction must emit the current v1 bundle
with additive optional lineage fields; the frozen fixture remains untouched.

---

## Explicit Approval Gates

1. Human approval of this Story and its Repository Analysis.
2. Human approval of the Implementation Plan.
3. Separate explicit approval for the first controlled persisted reconstruction,
   including the bounded Market Intelligence database writes.
4. Human review of the generated sample before any Benchmark Gate B activity.

Implementation must not begin before gates 1 and 2. Runtime reconstruction must
not run before gate 3. Benchmarking remains out of scope until gate 4.

---

## Implementation Preconditions

- The runtime preflight must verify the configured trend profile's required
  bias/setup windows. An evidence gap rejects the candidate; the profile is not
  changed.
- The offline command invokes `AnalysisExecutionService.register()`, then the
  existing claim/dispatch boundary, and polls the existing repository until a
  terminal state or the five-minute deadline. It does not add a network
  authority or public endpoint.
- The structured audit event records the trusted local launcher principal,
  explicit approval reference, request fingerprint, execution ID, selected
  evidence fingerprint set, terminal status, and output path. The event is
  bounded and contains no credentials, raw payloads, or individual large row
  dumps.
- The fixed output root and local launcher principal are configured before
  implementation validation; the command cannot accept an arbitrary path or
  self-assert a different operator identity.

These are specified implementation constraints, not open product decisions.
Changing them requires renewed Story/plan approval.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-057.md`
- `docs/architecture/adr/ADR-058.md`
- `docs/architecture/adr/ADR-060.md`
- `docs/architecture/adr/ADR-063.md`
- `docs/architecture/adr/ADR-067.md`

No new ADR is proposed. Existing decisions define historical known-at semantics,
feature projection immutability, strict replay boundaries, and protected source
evidence. A material ownership or temporal-policy change would require a new
ADR before implementation.

---

## Relevant Modules

- `market-data`
- `market-intelligence`
- `research/ml` only for validating the final canonical export after Java-side
  eligibility; no changes are planned in this Story.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Controlled reconstruction implementation completed.
- [ ] One bounded real sample is either strictly eligible or explicitly
      rejected with canonical evidence.
- [ ] Reproducibility and idempotency validation pass.
- [ ] Storage and security safeguards are verified.
- [ ] Independent Code Review completed.
- [ ] Human runtime reconstruction approval completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
