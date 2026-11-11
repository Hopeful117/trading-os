# Story 0227 - Implementation Plan

## Objective

Complete and validate the bounded, research-only HistData session continuity
policy described by Story 0227, using the existing candidate implementation at
`HEAD` as the starting point. The implementation must preserve strict
Market Data evidence while allowing one explicitly classified exploratory
weekend-session transition in an ML-01 feature-plus-outcome window.

## Current Candidate

The current `HEAD` commit (`480339c4`) contains the following candidate changes:

- `Ml01ExploratorySessionContinuityEvaluator` with the named policy and bounded
  transition rules;
- continuity-series integration in `Ml01HistDataPreparation`;
- feature, future and combined transition metadata;
- row, quality-report, manifest and fingerprint provenance;
- evaluator and preparation tests;
- export regression-test updates.

The current worktree is clean with respect to Story 0227 implementation files;
it contains two unrelated untracked ML-01.1 reports that must remain untouched.
The implementation phase must therefore inspect the committed candidate and
make only narrowly justified corrections within the approved Story scope.

The plan treats these changes as candidate implementation to verify and correct
within the approved Story scope. No unrelated worktree changes will be
reverted.

## Implementation Steps

### 1. Verify the policy contract

- Confirm the policy ID, version, source offset, 49-hour maximum gap and
  one-transition limit are centralized in the evaluator.
- Confirm exact adjacency returns `CONTIGUOUS`.
- Confirm only Friday 17:00 to Sunday 17:00-18:00 source-local transitions can
  return `ASSUMED_SESSION_TRANSITION`.
- Confirm dataset-edge, chronology, invalid-quality, observed-interior-activity
  and out-of-window cases fail closed.
- Confirm `permits` rejects unsupported classifications and more than one
  assumed transition.

### 2. Verify preparation integration

- Build observations from the existing HistData parser, H1 aggregation and
  Story 0226 admissibility decisions.
- Ensure strict-ineligible candles are not silently promoted to candidates.
- Ensure accepted history stops at strict disqualifying transitions and does not
  repair or synthesize candles.
- Ensure feature transition counts and feature elapsed duration are calculated
  from the accepted candidate sequence.
- Ensure future outcomes count transitions separately from feature history and
  reject unexplained, invalid or multiple-assumption windows.
- Ensure `CONTIGUOUS_ONLY` and `ASSUMED_SESSION_TRANSITION` are the only emitted
  exploratory strata.

### 3. Verify artifact contracts

- Include policy identity, version and parameters in row provenance, quality
  reports and dataset manifests.
- Preserve strict `UNEXPLAINED_GAP` diagnostics in quality reporting and keep
  `VERIFIED_CLOSURE` reserved for explicit closure evidence.
- Include policy material, transition limits and source checksums in dataset and
  row fingerprints.
- Verify published-bundle validation rejects missing or mismatched continuity
  policy metadata and fingerprint material.

### 4. Complete deterministic tests

- Keep unit coverage for valid and invalid evaluator boundaries.
- Keep integration coverage for a Friday-to-Sunday HistData fixture.
- Assert assumed research strata in rows and manifests.
- Assert strict unexplained-gap diagnostics in the quality report rather than
  treating them as exploratory row strata.
- Preserve existing contiguous, verified-closure, unexplained-gap, ambiguous
  evidence, scope and export regressions.

### 5. Reconcile documentation

- Review ADR-092, ADR-093, ADR-094, ADR-095 and Stories 0225-0226 against the
  implementation.
- Update only canonical documentation whose behavior is changed by Story 0227.
- Produce `implementation-report.md` with validation evidence and an explicit
  documentation outcome.
- Record a proposal-only vault outcome; do not modify the Obsidian vault.

## Files In Scope

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01ExploratorySessionContinuityEvaluator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01HistDataPreparation.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01ExploratorySessionContinuityEvaluatorTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01HistDataPreparationTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/HistoricalDatasetExportApplicationServiceTest.java`
- Story 0227 documentation artifacts only when required by reconciliation.

## Explicit Non-Goals

- No new parser, service, database migration, API or provider integration.
- No changes to strict Market Data authority or verified closure evidence.
- No model training, clustering, production feature snapshots or broker action.
- No source-file repair or synthetic observations.
- No Git commit, push, merge, PR creation or branch cleanup.

## Validation Plan

Run from `market-intelligence`:

```bash
mvn -q -Dtest=Ml01ExploratorySessionContinuityEvaluatorTest,Ml01HistDataPreparationTest,HistoricalDatasetExportApplicationServiceTest,Ml01H1AdmissibilityEvaluatorTest,HistDataOfflinePipelineTest test
mvn -q test
```

Run from the repository root:

```bash
git diff --check
```

Review the complete diff and confirm only approved files and Story artifacts
changed. Report existing integration-test warnings separately from failures.

## Expected Deliverables

- Corrected and validated Story 0227 implementation candidate.
- Deterministic continuity and preparation tests.
- `implementation-report.md` documenting implementation, validation and
  documentation reconciliation.
- Code Review Report after implementation review.
- Engineering Report only after the required workflow approvals and review.

## Gate

This plan requires explicit human approval before implementation or any further
code modification is performed.
