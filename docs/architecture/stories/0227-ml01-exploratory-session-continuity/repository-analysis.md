# Story 0227 - Repository Analysis

## Purpose

Assess the current repository and existing Story 0227 work before producing an
Implementation Plan. This analysis does not itself authorize a new
implementation scope or a later workflow stage.

## Workflow Context

- The canonical Story 0227 artifact was created in this step with explicit user
  authorization.
- No usable DevLog MCP capability or repository mapping is available in the
  current tool environment. Repository evidence, accepted ADRs and adjacent
  Story artifacts are therefore authoritative for current state.
- The Obsidian vault was not consulted; no personal-note context is required to
  determine this local Java implementation boundary.
- The worktree contains existing uncommitted Story 0227 implementation changes.
  They are preserved and are reviewed as the current candidate implementation;
  no commit, push, merge or branch cleanup has been performed.

## Repository State

- Branch: `main`
- Base branch state: synchronized with `origin/main` before the Story 0227 work
  was resumed.
- Current modified implementation files:
  - `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01HistDataPreparation.java`
  - `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01ExploratorySessionContinuityEvaluator.java`
- Current modified or added tests:
  - `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01HistDataPreparationTest.java`
  - `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01ExploratorySessionContinuityEvaluatorTest.java`
  - `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/HistoricalDatasetExportApplicationServiceTest.java`

## Authoritative Context

### ADR-095

ADR-095 permits a separate exploratory HistData population and a bounded
retrospective weekend-session convention. It does not authorize rewriting
strict Market Data evidence, claiming verified closure, or promoting research
outputs to production evidence.

### ADR-094

ADR-094 remains authoritative for strict continuity classifications and
verified closure evidence. The exploratory policy must consume strict facts and
must preserve unexplained gaps, unknown quality and invalid observations as
fail-closed results.

### ADR-093

ADR-093 remains authoritative for ML-01 chronological feature and future-window
semantics. H+4, H+12 and H+24 windows count valid H1 observations, preserve
actual timestamps and must not introduce leakage or synthetic repair.

### Stories 0225 and 0226

Story 0225 established the strict verified-closure crossing boundary. Story
0226 established the `ML01_HISTDATA_EXPLORATORY_V1` admissibility evaluator,
research feature schema, source checksums, export preparation and bundle
fingerprints. Story 0227 must consume those contracts and add only the
research-only session continuity layer.

## Current Implementation Evidence

The worktree already contains a focused implementation candidate:

- `Ml01ExploratorySessionContinuityEvaluator` defines the policy ID, source
  offset, 49-hour maximum gap, one-transition limit and classifications.
- `Ml01HistDataPreparation` builds a continuity series from existing parsed
  candles and Story 0226 admissibility decisions.
- Feature history and future outcomes carry transition counts, continuity
  strata and elapsed durations.
- Dataset manifests, quality reports, rows and fingerprints carry the policy
  provenance.
- Published-bundle validation verifies the continuity policy identity/version.
- Tests cover the evaluator and a Friday-to-Sunday integration fixture.

The implementation intentionally keeps strict unexplained-gap diagnostics in
the quality report while exposing assumed-session strata in research rows. This
is the required distinction between strict evidence and exploratory consumer
policy.

## Validation Evidence

Already executed successfully:

- Focused HistData, continuity evaluator, export, admissibility and offline
  pipeline tests.
- Full `market-intelligence` Maven test suite.
- `git diff --check`.

The full module suite emitted existing integration-test warnings about H2,
Mockito dynamic attachment, service discovery and expected concurrent unique
constraint races; no test failure was reported.

## Required Plan Boundary

The Implementation Plan should remain limited to:

1. verifying the existing candidate against each Story acceptance criterion;
2. identifying any necessary corrections in the evaluator or preparation
   integration;
3. preserving the current strict-versus-exploratory artifact boundary;
4. completing documentation reconciliation and the implementation report;
5. running the required quality validation and independent code review.

The plan must not add a new parser, closure provider, calendar service,
production API, MCP permission, model-training path or broker capability.

## Risks

- The Story 0227 implementation is already present as uncommitted work, so the
  plan must distinguish candidate implementation from formally approved Story
  scope.
- A weekend assumption must never be serialized or interpreted as
  `VERIFIED_CLOSURE`.
- Future-window transition counting must remain bounded to valid admissible H1
  candidates and must fail closed on unexplained gaps or multiple assumptions.
- Fingerprint material and published-bundle validation must remain synchronized
  with policy metadata.
- Existing contiguous behavior must remain unchanged.

## Recommendation

The repository contains enough evidence to produce a narrow Implementation Plan
without an architectural decision or additional product clarification. Human
approval is required for this Repository Analysis before the Implementation
Plan is produced.
