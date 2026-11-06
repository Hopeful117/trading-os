# Implementation Plan - Story 0213

## Plan Status

Prepared for human approval. This document is a design and implementation plan
only. No historical reconstruction, database write, service restart, or code
change is authorized by this plan.

## Approach

Implement one controlled persisted reconstruction vertical slice inside the
existing `market-intelligence` application. Reuse the existing strict Market
Data client, context contributors, deterministic capability execution,
`FeatureSnapshotProjectionService`, `MarketOutcomeEvaluationService`, and
canonical research exporter. Do not create a parallel offline eligibility
implementation, new service, public endpoint, scheduler, or Order Book path.

The first proof uses one preflight-selected real candidate:

```text
market: 8b336ec3-1557-4432-b052-ae569e041dac
interval: FIFTEEN_MINUTES
cutoff: 2026-11-06T15:45:00Z
horizon: PT1H
policy: STRICT_OBSERVED
```

The command must revalidate the candidate from persisted evidence at runtime;
these values are not permission to bypass preflight or to select a favorable
outcome.

## 1. Add Explicit Reconstruction Provenance

- Add a small execution-origin value object or enum with at least
  `CONTEMPORARY_ANALYSIS` and `HISTORICAL_RECONSTRUCTION`.
- Extend `AnalysisExecutionProvenance` and the request path additively with
  `executionOrigin` and `reconstructionRequestFingerprint`.
- Preserve deserialization of older execution payloads by defaulting missing
  origin to `CONTEMPORARY_ANALYSIS`; never reinterpret old executions as
  reconstructed.
- Require explicit reconstruction origin for the controlled command.
- Include origin in the reconstruction idempotency key and audit output, but do
  not include execution UUID or wall-clock creation time in semantic feature or
  outcome fingerprints.
- Reuse existing `analysisExecutionId` links on `FeatureSnapshot` and
  `ObservedMarketOutcome` rather than adding a new artifact table.
- Extend canonical JSONL lineage with reconstruction origin and execution ID by
  resolving the linked execution through the existing repository boundary.
- Extend `HistoricalOhlcEvidenceResponse`, `OhlcResponse`, `ContextProvenance`,
  and `ContextSectionSummary` so `fetchedAt`, `capturedAt`, and `knownAt` remain
  distinct. Remove the current historical-source mapping that assigns `knownAt`
  to the `fetchedAt` field.
- Extend bounded future-range validation with source identity and acquisition
  timestamp metadata for validation/audit. Keep the existing aggregate future
  evidence fingerprint as the outcome identity.
- Extend the historical OHLC-to-structure lineage so every
  `market-structure-v2` candle/reference retains observation ID, observation
  fingerprint, source identity, captured origin, normalization version,
  `fetchedAt`, `capturedAt`, and `knownAt`. Reject the structure artifact when
  any required field is missing or `knownAt > evaluationCutoff`.

## 2. Add Controlled Application Use Case

- Add an application-level command/use case in `market-intelligence`, not a new
  microservice.
- Accept exactly one market, cutoff, `FIFTEEN_MINUTES` research interval,
  `PT1H` horizon, strict policy, `--confirm-historical-reconstruction`, and a
  non-empty human approval reference. Accept only a run identifier; resolve it
  below a configured non-user-writable output root and reject traversal,
  symlinks, existing directories, and paths outside that root.
- Reject all multi-market, multi-cutoff, non-strict, non-canonical horizon, and
  unsupported interval requests before persistence.
- Use the existing `AnalysisExecutionService` registration and dispatcher path
  so execution lifecycle, status transitions, trace metadata, and idempotency
  remain canonical.
- Invoke `register()`, `claimForDispatch()`, and `dispatchRegistered()` through
  the application boundary, then poll the existing execution repository until
  terminal completion or the five-minute deadline. Do not project, evaluate, or
  export before completion.
- Do not expose the command through the Gateway or a public controller. Use a
  disabled-by-default offline command runner. Require
  `--confirm-historical-reconstruction` and a non-empty human approval
  reference. Obtain the operator principal from the trusted local process
  launcher, not from a user-supplied argument; record it with the approval
  reference, request fingerprint, execution ID, terminal status, and resolved
  output path in a bounded structured audit event.

## 3. Implement Bounded Preflight

- Verify the fixed market identity through existing Market Data contracts.
- Request strict historical OHLC with the existing cutoff and bounded limits.
- Reuse current trend role configuration and its existing maximum 720-candle
  limit; do not assume the Gate C 20-row diagnostic set is sufficient for
  `TrendContextProfile` minimums.
- Verify every selected feature row has closed, non-synthetic, captured origin,
  supported normalization, source identity, fingerprint, and
  `knownAt <= cutoff`.
- Verify the complete `PT1H` future range contains the expected four contiguous
  15-minute observations and no gaps.
- Verify every future row has source identity, fingerprint, non-null
  `fetchedAt`, `capturedAt`, and `knownAt`; future rows may be known after the
  cutoff but remain outside all feature/context inputs.
- Verify future rows are not passed to feature/context construction.
- Persist the ordered feature and future observation references and their
  aggregate selection fingerprint in the reconstruction request. The historical
  acquisition contract must return and assert those references; a response
  selected only by `asOf + limit` is rejected when its identity set differs.
- Fail before execution persistence if any preflight condition is false.

## 4. Reuse Historical Context And Deterministic Capabilities

- Pass `STRICT_OBSERVED` and the explicit cutoff through the existing
  `IntelligenceAnalysisRequest` and `AnalysisExecutionProvenance`.
- Assert the immutable reconstruction plan contains exactly the complete strict
  capability dependency closure `trend-context-analysis`,
  `market-structure-analysis`, `ohlc-range-analysis`, and
  `ohlc-momentum-analysis`, with historical OHLC and Trend Context only. Reject
  current snapshot, spread, news, order-book, live-provider, or unlisted
  capability requirements before dispatch.
- Enforce the allowlist in the reconstruction application boundary and in the
  execution plan validator; do not rely on a caller-provided capability list.
- Reuse `HistoricalOhlcEvidenceSource` and
  `HistoricalOhlcContextContributor` for historical OHLC context.
- Reuse `TrendContextRoleHistoryContextContributor` for profile-based role
  history and its current bounded retry/limit behavior.
- Preserve `fetchedAt`, `knownAt`, source reference, normalization version,
  evidence policy, aggregate fingerprint, observation count, and cutoff in
  `ContextSectionSummary`.
- Do not add synthetic Market Snapshot, News, Order Book, AI, or broker context.
- Require deterministic artifact production to complete before projection.
- Require an available, non-degraded `market-structure-v2` artifact from the
  same execution before projection. Keep `FeatureSnapshotProjectionService`
  unchanged for existing callers; enforce this additional precondition only in
  the reconstruction use case.

## 5. Project One Feature Snapshot

- Call `FeatureSnapshotProjectionService.project(executionId)` after the strict
  execution reaches a usable terminal result.
- Reuse `FeatureProjectionV1` and all existing artifact validation for market,
  context fingerprint, producing execution, and artifact cutoff.
- Preserve actual `createdAt` from the runtime clock and the historical
  `evaluationCutoff` separately.
- Confirm the snapshot links to the reconstruction execution and carries
  complete analyzer provenance.
- Use `saveIfAbsent` and fail closed on conflicting semantic content.
- Do not treat the projection service's existing support for an unavailable
  structure group as sufficient for reconstruction eligibility.

## 6. Evaluate One Independent Outcome

- Call `MarketOutcomeEvaluationService` with the same market/cutoff,
  `OutcomeHorizon(Duration.ofHours(1))`, and reconstruction execution ID.
- Reuse `HistoricalOhlcReferenceEvidenceSource`,
  `HistoricalOhlcFutureEvidenceSource`, and `MarketOutcomeCalculator`.
- Require reference evidence at or before the cutoff.
- Require exactly four complete 15-minute future observations from the cutoff
  through maturity, with captured origin, closed/non-synthetic state, and
  `historical-ohlc-v1`.
- Persist no available outcome when future coverage is incomplete; preserve the
  canonical unavailable/incomplete state and reason only if the lifecycle
  contract requires a diagnostic artifact.
- Verify the outcome fingerprint is stable and does not contain feature values
  or execution wall-clock fields.
- If `saveIfAbsent` finds an outcome with the same semantic fingerprint linked
  to another reconstruction identity, reject the attachment and report the
  conflict instead of silently reusing it.

## 7. Run Canonical Research Eligibility

- Build a one-sample `ResearchPopulationSpec` with `STRICT_OBSERVED`,
  `feature-projection-v1`, `market-outcome-v1`, `PT1H`, and exact selected
  feature/outcome fingerprints.
- Call the existing `ResearchDatasetExportService` and
  `ResearchDatasetBuilder`; do not reproduce eligibility checks in the new
  command.
- Require the existing temporal evidence map to resolve `knownAt` from persisted
  context and verify it is no later than the cutoff.
- Emit exactly one canonical JSONL sample or an explicit canonical exclusion.
- Include reconstruction origin and execution lineage in the export without
  changing the frozen negative bundle-v2 fixture. The active exporter contract
  is `research-dataset-bundle-v1`; emit that version unless an approved contract
  change separately establishes a new version.
- Add lineage fields additively to the existing JSONL document and validator;
  keep `research-dataset-v1` and sample fingerprint material unchanged unless
  contract tests prove a version bump is required. Reconstruction samples must
  contain origin and execution lineage; legacy samples remain readable without
  those optional fields.

## 8. Idempotency And Failure Handling

- Use one deterministic idempotency key for market, cutoff, projection version,
  horizon, policy, and reconstruction origin.
- Persist a canonical request fingerprint containing those fields plus the
  frozen capability allowlist and preflight source fingerprint set.
- Return an existing terminal result for a repeated identical request only
  after comparing the stored request fingerprint and source set.
- Make registration an atomic compare-or-conflict operation on idempotency key
  and request fingerprint. A concurrent or conflicting request must not create
  a second execution or attach an existing snapshot/outcome by fingerprint.
- Reject a conflicting request sharing the semantic identity but differing in
- evidence policy, cutoff, horizon, projection, allowlist, or source fingerprint.
- Do not reuse an expired or failed execution for a new attempt. A rerun uses a
  new attempt key and is allowed only after checking that no successful semantic
  snapshot/outcome/sample already exists.
- Do not automatically retry failed writes or failed remote calls.
- Preserve partial diagnostics without presenting a partial result as eligible.
- Enforce the 30-second per-read and five-minute total command budgets.
- At the five-minute deadline, invoke the existing cancellation transition when
  the execution is cancellable, verify the persisted terminal state, and refuse
  projection, outcome evaluation, and export unless completion was already
  valid. A timeout must not leave a worker continuing into downstream writes.
- Enforce one in-process execution and rely on the existing idempotency
  boundary for duplicate cross-instance requests.

## 9. Security And Operations

- Keep the command outside public and Gateway routes.
- Do not accept ordinary user JWTs as reconstruction authorization.
- If an internal trigger is unavoidable, require a dedicated service authority,
  explicit operator initiation, input validation, and bounded audit logging.
- Do not log credentials, raw provider payloads, or individual large evidence
  rows.
- The audit event retains the bounded ordered source-reference/fingerprint set
  and its three acquisition timestamps without storing raw OHLC payloads.
- Keep automatic lifecycle scheduling disabled for the proof.
- Do not request or release Order Book retention dependencies.
- Enforce the 100 MB temporary-output budget and stop before exceeding it.
- Use the existing bundle exporter atomic temporary-directory move; an existing
  output directory is a conflict, never an overwrite.
- Write at most one execution lifecycle, one result per allowlisted capability,
  one feature snapshot, one outcome, one audit event, and one small export. Stop
  before each write when the budget is exceeded; partial artifacts are marked
  non-exportable and are not silently retried.

## 10. Tests

Add focused tests before any runtime proof:

- request scope, origin, policy, horizon, and idempotency validation;
- strict `knownAt` cutoff acceptance and rejection;
- missing provenance, synthetic, open, mixed-version, and conflicting evidence;
- trend role window insufficiency and bounded limit behavior;
- context provenance propagation and retrospective `createdAt` semantics;
- projection reuse and semantic fingerprint stability;
- future outcome separation, four-candle completeness, gap rejection, and
  `market-outcome-v1` fingerprint stability;
- market-structure dependency closure, complete OHLC provenance propagation,
  `knownAt` cutoff rejection, conflicting duplicate provenance rejection, and
  reconstruction rejection when `market-structure-v2` is missing or degraded;
- repeated invocation and conflicting duplicate behavior;
- canonical dataset export with one eligible sample and canonical exclusions;
- no Order Book, LLM, broker, or collection interaction.

Use isolated PostgreSQL fixtures for persistence tests. Do not point tests at the
85 GB production-like Market Data volume.

## 11. Validation Sequence

1. Run focused Market Intelligence unit tests.
2. Run isolated persistence/integration tests.
3. Run the affected Market Intelligence Maven suite.
4. Run `git diff --check`.
5. Obtain explicit approval for the bounded runtime operation.
6. Perform exactly one local reconstruction run against the fixed candidate.
7. Verify service/container health, artifact counts, lineage, fingerprints, and
   storage delta.
8. Repeat the same request with the same idempotency key and verify no duplicate
   artifacts.
9. Stop and present the sample or canonical rejection for human review.

No benchmark, broad population export, provider call, Order Book replay, or
deployment is part of this sequence.

## 12. Expected Deliverables

- Additive execution-origin and lineage contract changes.
- Controlled bounded reconstruction use case and offline invocation mechanism.
- Focused tests for all temporal, identity, idempotency, failure, and export
  guarantees.
- One runtime validation record only after explicit operational approval.
- Updated implementation report and independent code review.

## 13. Risks And Mitigations

- **Retrospective artifact confusion:** explicit origin, actual creation times,
  and exported lineage distinguish reconstruction from contemporaneous analysis.
- **Future leakage:** separate feature and outcome requests plus contract tests
  enforce the cutoff boundary.
- **Logic duplication:** all projection, outcome, and export decisions remain in
  existing services.
- **Unexpected feature history demand:** use profile limits and fail closed;
  never lower minimum candles for the sample.
- **Database growth:** one execution, one snapshot, one outcome, one sample, and
  a hard temporary-output budget.
- **Duplicate runs:** deterministic idempotency key and `saveIfAbsent` paths.
- **Operational authority expansion:** no public endpoint; dedicated explicit
  approval required for the only runtime invocation.
- **Storage incident:** no Order Book access, migration, cleanup, or collection.

## 14. Approval Boundary

This plan is not implementation approval. Human approval is required for this
plan before production code changes. A second explicit human approval is
required before the single bounded runtime reconstruction that writes derived
Market Intelligence artifacts.
