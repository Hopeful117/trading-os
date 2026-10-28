# Repository Analysis - Story 0172

## Baseline

- Repository root: `/home/ludo/workspace/trading-os`.
- Current branch: `main`, aligned with `origin/main` at
  `d7e3672222e3c147479536b4443eca39a6dae128`.
- The worktree is not clean. It contains pre-existing changes in Trading Core,
  Market Intelligence scan scope, challenge dashboard, scan panel, Docker
  Compose, and preserved rehearsal capture files. These must not be discarded
  or included automatically in Story 0172.
- DevLog applicability was checked for Story 0172, but the Story Agent failed
  because its upstream provider was rate-limited with HTTP 429. No DevLog
  evidence is used as project authority.

## Story And Architecture

Story 0172 is a focused projection and presentation change. It does not alter
Market Thesis composition, persistence, state semantics, Risk, TradePlan
validation, or execution authority.

ADR-076 establishes `Market Thesis` as the deterministic, market-scoped,
versioned analytical authority. Its `NO_TRADE`, `WATCH`, and `CANDIDATE` states
are analytical outcomes and must not be presented as execution authorization.

The existing `OpportunityController` already resolves a referenced Thesis for
the detail endpoint. The active endpoint instead calls `response(value, false)`
and therefore returns only the Thesis reference. This is the confirmed source
of the active-list `Not evaluated` defect.

## Existing Contracts

- `OpportunityResponse` supports both `marketThesis` reference and expanded
  `thesis` response fields.
- `MarketThesisResponse` exposes state, reasons, supporting evidence,
  contradicting evidence, and missing evidence.
- `OpportunityDetail.canPrepare()` permits preparation only when the
  Opportunity is `ACTIVE` and the Thesis state is `CANDIDATE`.
- `OpportunityTradePlanGenerationService` independently validates the Thesis
  reference, freshness, identity, scenario compatibility, and `CANDIDATE`
  state on the backend.

## Affected Changes

The focused Story 0172 changes are:

- `market-intelligence/.../OpportunityController.java`: include expanded Thesis
  in `/api/v1/opportunities/active` responses.
- `trading-os-web/src/app/features/opportunities/opportunities.html` and
  `.scss`: make the Thesis decision primary in the active list and label score
  as signal strength.
- `trading-os-web/src/app/features/opportunities/opportunities.ts`: safely
  handle legacy partial Thesis arrays.
- `trading-os-web/src/app/features/opportunities/opportunity-details/*`: show
  decision reasons and evidence categories, preserve the TradePlan gate, and
  improve narrow-mobile layout.
- `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.spec.ts`:
  cover `NO_TRADE`, evidence rendering, and blocking preparation for `WATCH`.

Other currently modified files are outside this Story and require separate
human disposition before commit or branch creation.

## Findings And Risks

- The active-list projection now has the correct data path, but a focused
  backend assertion for an active Opportunity with a resolvable Thesis should
  be added before final Story completion.
- Existing frontend test fixtures contain partial Thesis objects. The new
  `safeList()` fallback preserves truthful rendering for those payloads.
- The active list currently shows only the first Thesis reason, while the detail
  view shows all reasons. This is consistent with the compact list presentation
  but should remain intentional.
- Production build warnings remain for the existing bundle and component style
  budgets; they are warnings, not blocking failures.

## Validation Evidence

- Angular test suite: 56 test files and 562 tests passed.
- Angular production build: passed, with existing budget warnings.
- Targeted Market Intelligence tests:
  `OpportunityControllerTest` and `OpportunityControllerMappingTest` passed,
  4 tests total.
- `git diff --check`: passed.

## Scope Assessment

The focused changes satisfy the intended Story boundary and preserve the
Market Thesis, Risk, TradePlan, and execution responsibility boundaries. The
Story is suitable for human approval of the Repository Analysis, subject to
adding the direct active-list Thesis projection assertion and isolating the
Story files from unrelated worktree changes before delivery.

## Approval State

This Repository Analysis is ready for human approval. Implementation and Story
closure must not advance to commit or integration until the human explicitly
approves this analysis and confirms which pre-existing worktree changes are
excluded from Story 0172.
