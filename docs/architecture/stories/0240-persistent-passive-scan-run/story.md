# Story 0240 - Persist the PassiveScanRun Lifecycle

## Metadata

**ID:** `0240`

**Title:** Persist the PassiveScanRun Lifecycle

**Status:** Implementation complete - independent code review evidence missing

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-023`, `ADR-033`, `ADR-057`, `ADR-102`

**Predecessor:** `0239`

**Repository Analysis:** `repository-analysis.md`

**Implementation Plan:** `implementation-plan.md`

**Implementation Report:** `implementation-report.md`

**Repository Analysis Approval:** Approved by the project owner on 2026-11-13.
The Implementation Plan was approved by the project owner on 2026-11-13.
Implementation is complete within the approved scope; independent code review
and final human validation remain pending. The implementation was included in
PR #292, whose required CI checks passed, and merged into `main` at merge commit
`c28a246d91bb0f1a77c7a053c63ea305cdb4aaef`. GitHub reports no submitted review
for PR #292 (`reviews: []`, empty review decision), so the merge and CI results
are not treated as proof of an independent code review. No recurring scheduler,
retry worker, public endpoint, broker operation or runtime activation was added.

## Goal

Introduce the smallest durable operational passive orchestration boundary above
single-market `AnalysisExecution(PASSIVE)`.

## Product Value

Passive work becomes auditable, retryable and restart-safe instead of being an
implicit scheduler loop.

## Existing Components Reused

- `ActiveScan` and `ActiveScanMarket` lifecycle patterns.
- `AnalysisExecutionService.register` and deterministic idempotency.
- `AnalysisExecution` persistence and provenance.
- Existing pipeline and execution status contracts.

## Scope

- Define `PassiveScanRun` and `PassiveScanMarket` domain contracts.
- Persist immutable universe fingerprint and ordered market snapshot.
- Persist one logical child linkage per selected market.
- Define run and child states from ADR-102.
- Add run-level and child-level deterministic idempotency.
- Persist linkage before any dispatch handoff.
- Add bounded read projection for operational status.
- Preserve explicit insufficient-evidence versus failure outcomes.

## Out of Scope

- Recurring scheduler invocation.
- Retry worker and provider rate limiting.
- Dashboard UI.
- New candidate aggregate.
- ActiveScan changes.
- Broker, account, risk or execution operations.

## Persistence Changes

Add only the passive run and child tables required by the lifecycle, with
foreign keys, uniqueness constraints and immutable snapshot fields. Do not
alter the meaning of existing ActiveScan tables.

## Authorization

The initial internal application seam must be callable only by the approved
passive scheduler boundary. No public user endpoint is required in this Story.

## Acceptance Criteria

- [ ] One logical run key creates exactly one PassiveScanRun.
- [ ] Retrying the same run request does not duplicate child executions.
- [ ] A different universe fingerprint under the same logical key is rejected.
- [ ] Every child preserves canonical market/provider/product identity.
- [ ] Run and child rows are durable before dispatch is possible.
- [ ] Run states distinguish no work, partial completion, failure and success.
- [ ] Insufficient evidence is not represented as successful analysis.
- [ ] Restart/reload can reconstruct all unfinished run and child state.
- [ ] Concurrent claims cannot create duplicate logical children.
- [ ] Persistence, idempotency and state-transition tests pass.

## Dependencies

Depends on Story 0239. Blocks Stories 0241 and 0242.

## Migration and Safety

Migration must be additive. Existing analysis and ActiveScan records remain
untouched. No runtime schedule is enabled by this Story.

## Validation

- Domain state-transition tests.
- Spring persistence and uniqueness-race tests.
- Restart reconstruction tests.
- `git diff --check` and migration validation.
