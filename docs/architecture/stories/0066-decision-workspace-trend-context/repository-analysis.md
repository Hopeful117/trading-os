# Repository Analysis: Story 0066

## Scope and Authority

Story `0066-decision-workspace-trend-context/story.md` is the canonical Story
for identifier `0066`. The other directory previously named
`0066-market-structure-investigation` described a different investigation and
was not compatible with the canonical Story. This analysis is reconciled to the
canonical frontend Decision Workspace scope.

The current repository is authoritative for implementation state. Story `0064`
provides the Trend Context observation/read boundary, and ADR-048 governs the
distinction between analytical evidence, human decisions, Risk, and execution.

## Current Frontend Workspace

### Confirmed

`DecisionWorkspace` already provides:

- account selection and account refresh;
- account-scoped context resolution;
- eligible and excluded market display;
- selected-market loading and eligibility checks;
- market identity, tradability state, and constraints;
- ticker, OHLC history/live candle, order book, and recent trades;
- timeframe and order-book-depth selection;
- reactive stream teardown when the account or market changes;
- manual Trade Ticket rendering inside the selected market context;
- explicit loading, error, waiting, and unavailable states for existing data.

The component uses standalone Angular imports, RxJS streams, `switchMap`,
`combineLatest`, `shareReplay`, `AsyncPipe`, and URL query parameters for the
selected account and market. This is the established pattern to extend.

### Existing files to reuse

- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.scss`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts`
- `trading-os-web/src/app/core/services/decision-context.service.ts`
- `trading-os-web/src/app/core/services/market.service.ts`
- `trading-os-web/src/app/core/models/decision-context.model.ts`

## Existing Trend Context Read Contract

### Confirmed backend capability

Market Intelligence already exposes an authenticated endpoint:

```text
GET /api/v1/intelligence/trend-context/{marketId}
```

`MarketIntelligenceController` requires an authenticated actor before returning
`TrendContextReadModel`. The Gateway already routes `/api/v1/intelligence/**`
to Market Intelligence, so no new Gateway route appears necessary.

`TrendContextReadModel` currently contains:

- `marketId`;
- operational status;
- assessment presence and validity;
- observation ID, lineage ID, version, status, and validity window;
- current `TrendContextAssessment` when current;
- `lastSuccessfulAssessment` when historical data exists.

`TrendContextReadService` composes the latest Trend Context observation with the
latest Trend Context analysis execution. It distinguishes operational
availability/staleness from whether an assessment is current. It does not
fabricate an assessment when the current execution is unavailable or failed.

### Important contract limitation

The frontend Story requires account and market selection, but the current read
endpoint is market-scoped. The selected account remains important for market
eligibility and UI context, while the assessment request itself currently uses
the selected market ID. If account-specific assessment ownership or authorization
is required by the approved contract, that is a backend/API decision and must
not be silently invented in the Angular service.

The frontend model should type the actual read contract rather than expose raw
backend persistence objects or provider payloads.

## ADR-048 Authority Requirements

The panel must visibly preserve these distinctions:

```text
CONTEXTUALLY_ATTRACTIVE != RISK_APPROVED != EXECUTION_AUTHORIZED
```

The assessment is deterministic analytical evidence. It may contain direction,
regime, phase, attention, structural evidence, exclusions, invalidation,
freshness, and provenance. It must not create or imply:

- `StrategyMatch`;
- `TradingOpportunity`;
- `TradePlan`;
- Risk approval;
- `ExecutionIntent`;
- broker commands.

The existing manual ticket must remain a separate human-controlled workflow.
Opening it must not hide or mutate the Trend Context assessment.

## Required Frontend Contract Shape

The repository has no Trend Context Angular model or service yet. Story 0066
should add a typed model representing the projection supplied by the backend,
including at least:

- operational state: available, missing, stale, unavailable, failed, or in-progress;
- assessment presence/currentness and validity;
- assessment timestamp and validity window;
- direction, regime, phase, and attention outcome;
- BIAS, SETUP, and optional TRIGGER role assessments;
- EMA and ATR evidence;
- alignment and contradictions;
- hard exclusions and data-quality findings;
- confirmed structural references and invalidation;
- provenance summary and evidence references;
- concise explanation/why data when supplied by the backend.

The model must preserve `null`/absence for unavailable data. It must not map
unavailable, stale, invalid, or incomplete states to a favorable direction.

## Reactive Integration Boundary

The smallest compatible implementation is a service method such as:

```text
findTrendContext(marketId): Observable<TrendContextReadModel>
```

The workspace should derive an assessment view from the selected eligible market
with `switchMap`, so a market change cancels the previous request and clears
the previous assessment while the new one loads. The stream should be composed
from the existing `selectedMarket$`/`marketView$` boundary rather than from a
manual subscription.

Required state behavior:

- no account: no assessment request;
- no selected market: no assessment request and no previous assessment shown;
- selected market loading: assessment loading/cleared;
- ineligible market: no assessment request;
- selected eligible market: request the market-scoped assessment;
- market change: previous assessment must not remain visible as current;
- account change: selected market and assessment must clear;
- request error: show unavailable/error, not `NO_SETUP` or a fabricated direction;
- valid assessment with `NO_SETUP`, `WATCH`, or `UNKNOWN`: show the actual outcome;
- optional TRIGGER absent: show unavailable, never confirmation.

The service should use the existing `environment.gatewayUrl` convention and the
same authenticated HTTP client path as the other frontend services.

## Workspace Rendering Boundary

The current workspace has a `market-context` container with the market header,
manual ticket/actions, ticker, chart, order book, and recent trades. The new
Trend Context panel should be added inside this selected-market context before
or adjacent to the existing trading actions, while preserving the existing
market sections and responsive layout.

The panel should be a focused assessment presentation, not a new workspace
redesign. It should make analytical status visually distinct from the existing
market tradability status and from manual Trade Plan actions.

Recommended presentation groups:

- assessment identity, timestamp, freshness, validity, and provenance;
- direction, regime, phase, and attention outcome;
- role cards for BIAS, SETUP, and optional TRIGGER;
- EMA/ATR and structural evidence;
- alignment, contradictions, exclusions, and invalidation;
- concise why/evidence explanation.

The existing dark-slate/blue visual language and responsive grid conventions in
`decision-workspace.scss` should be extended rather than replaced.

## Existing Test Baseline

`decision-workspace.spec.ts` already covers:

- account selection gating;
- account-scoped market loading;
- excluded-market protection;
- selected market loading;
- existing market streams;
- market facts and live section rendering;
- existing data errors without fabricated values;
- market switching and stream teardown;
- manual ticket opening inside the selected context.

The Story requires additional focused tests for:

- Trend Context service URL and market parameter;
- typed successful assessment response;
- loading and cleared state;
- unavailable/stale/invalid/degraded responses;
- valid `NO_SETUP`, `WATCH`, and `UNKNOWN` outcomes;
- optional TRIGGER absence;
- account and market selection changes;
- authority-label semantics;
- preservation of existing market sections and manual ticket behavior.

Mocks must be extended without weakening current stream and manual-ticket
coverage.

## Scope Gaps and Risks

### Confirmed gaps

- No Angular Trend Context model exists.
- No Angular Trend Context service exists.
- `DecisionWorkspace` does not currently request or render Trend Context.
- Current frontend tests have no Trend Context fixture or state coverage.
- The current backend read endpoint is market-scoped rather than account-scoped.

### Risks

- Rendering `assessment.attention` beside the manual trade button without an
  authority label could imply authorization.
- Reusing the previous market's assessment during `switchMap` transitions could
  show stale analytical evidence for the newly selected market.
- Treating `UNAVAILABLE`, `STALE`, or `INVALID` as `NO_SETUP` would collapse
  operational failure into a valid analytical outcome.
- Rendering `lastSuccessfulAssessment` as the current assessment could violate
  the backend read-service semantics.
- Reformatting the whole workspace or changing existing stream behavior would
  exceed Story scope and create regression risk.
- Adding frontend calculations for direction, regime, structure, EMA, ATR, or
  freshness would violate Market Intelligence authority.
- Adding Strategy, Opportunity, TradePlan, Risk, or execution actions from the
  panel would violate ADR-048 and the Story's explicit out-of-scope boundary.

## Smallest Implementation Boundary

1. Add the typed frontend Trend Context projection model and explicit UI state model.
2. Add one reactive service method for the existing authenticated read endpoint.
3. Add a selected-market-derived Trend Context stream to `DecisionWorkspace`.
4. Render one focused assessment panel with truthful operational and analytical states.
5. Preserve the existing market data sections and manual Trade Ticket unchanged.
6. Add focused service/component tests and run the Angular check/build plus `git diff --check`.

No backend calculation, persistence, API redesign, Gateway route, Strategy, Risk,
TradePlan, execution, chart replacement, or broad visual redesign is justified
by the current Story.

## Explicit Non-Goals

- No new Trend Context calculation.
- No new backend persistence.
- No account-specific backend contract invented without approval.
- No StrategyEvaluation, StrategyMatch, TradingOpportunity, TradePlan, Risk, or execution change.
- No automatic trade action or risk decision.
- No chart, ticker, order-book, recent-trades, or account-context replacement.
- No scanner, ranking, news, ML, LLM, agent, or position-monitor UI.
- No frontend-derived analytical facts.

## Reconciliation Result

- Canonical Story retained:
  `docs/architecture/stories/0066-decision-workspace-trend-context/story.md`
- Repository analysis reconciled into the same Story directory:
  `docs/architecture/stories/0066-decision-workspace-trend-context/repository-analysis.md`
- Conflicting duplicate removed:
  `docs/architecture/stories/0066-market-structure-investigation/`
