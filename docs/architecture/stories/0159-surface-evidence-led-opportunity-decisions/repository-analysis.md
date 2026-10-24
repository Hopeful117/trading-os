# Repository Analysis - Story 0159

## Baseline

- `main` and `origin/main` are both at `14aaf6ebf7c699881db098a5d99cd3bb2ee0d773`.
- Story 0157 and Story 0158 are present and merged.
- The worktree was clean before this analysis.
- DevLog applicability was checked for the next Trader Cockpit decision-support
  increment, but the Story Agent failed because its upstream provider was
  rate-limited with HTTP 429. No DevLog evidence is used as project authority.
- No Obsidian write is proposed.

## Existing Opportunity Contract

`trading-os-web/src/app/core/models/opportunity.model.ts` already exposes the
deterministic facts needed by the Cockpit:

- direction, scenario, timeframe, type, status, and score;
- explanation;
- evaluated, created, and valid-until timestamps;
- setup reference price and setup triggers;
- strategy-match provenance;
- observation and analysis execution references;
- optional market, account, and scan provenance.

`OpportunityService` already reads the authenticated existing API. The backend
`OpportunityController` exposes `/api/v1/opportunities/**` and the Market
Intelligence security configuration requires an authenticated JWT for that
surface. No new endpoint or authorization rule is required for this Story.

## Existing Trend Context Evidence

`trading-os-web/src/app/core/models/trend-context.model.ts` and
`TrendContextService` already provide the selected market's read model. The
current contract includes operational status, validity, freshness/diagnostic
information, timeframe assessments, supporting evidence, contradictions, and
invalidations.

`DecisionWorkspace` already loads Trend Context after the selected market is
resolved and already renders a details disclosure. The implementation should
reuse this request and read model rather than create another analysis path.

## Current Cockpit Gap

`DecisionWorkspace` currently owns the correct context boundaries:

- selected account, market, and Opportunity route state;
- Opportunity loading and market consistency checks;
- Trend Context loading;
- OHLC, ticker, Order Book, and Recent Trades stream lifecycle;
- navigation to Trade Plan preparation.

The current template presents the active Opportunity as a compact summary and
offers `Prepare from opportunity`. Existing Trend Context evidence is available
but is not organized as an explicit pre-decision investigation flow. There is no
current explicit `NO TRADE` presentation state in the Cockpit.

The smallest coherent change is therefore a frontend presentation change in the
existing coordinator and its template/styles/tests. No second Opportunity
detail authority, scanner, stream owner, or backend projection is needed.

## Canonical Action Boundaries

`TradePlanService.createFromOpportunity()` is the existing Opportunity →
TradePlan seam. `PlanPage` remains responsible for the canonical Trade Plan
workflow and human decision provenance. `PREPARE TRADE` must continue through
that seam.

No canonical persistent Opportunity-level `NO TRADE` command or aggregate was
found in the repository. This Story therefore defines `NO TRADE` only as a
current workspace presentation state. It must not call a backend mutation,
create a TradePlan, alter Risk, or imply execution rejection.

The presentation decision must be cleared when the active market or Opportunity
changes. It must also be cleared when the Opportunity is unavailable or no
longer matches the selected market.

## Responsibility and Security Findings

- `DecisionWorkspace` is the existing context coordinator and must retain
  account/market/Opportunity ownership.
- Opportunity and Trend Context data remain authenticated existing service
  contracts; frontend state is not an authorization boundary.
- `PREPARE TRADE` remains human validation, not automatic execution.
- `NO TRADE` is intentionally non-persistent because no approved domain
  authority exists for a new Opportunity-level decision.
- No credentials, auth bypass, public endpoint, or provider-specific contract is
  introduced.

## Persistence and Architecture Assessment

- Opportunity persistence: `FOLLOW_UP_REQUIRED`, not a blocker. Existing
  projections are sufficient for this presentation slice.
- Trend Context persistence: `FOLLOW_UP_REQUIRED`, not a blocker. Existing read
  model evidence is sufficient.
- Human Opportunity-level decision persistence: `NOT ESTABLISHED`. Adding it
  would require a new domain/API decision and is explicitly out of scope.
- New ADR: not required. This Story composes existing contracts and preserves
  current responsibility boundaries.
- Implementation Plan: likely not required after approval because the Story,
  repository analysis, existing contracts, and component conventions provide
  sufficient implementation guidance. Reassess only if implementation reveals
  an unestablished architectural decision.

## Risk and Freshness Handling

- Stale or expired Opportunity evidence must be visibly stale/invalid and must
  not be rendered as an approved trade.
- Unavailable Trend Context must remain distinct from contradiction or rejection.
- Contradictions and invalidations must be rendered from the read model without
  Angular recomputation.
- No new timer, polling loop, or market stream is justified by this Story.

## Validation Plan

- Add focused component tests for evidence sections and state transitions.
- Assert `PREPARE TRADE` invokes the existing TradePlan service/navigation path.
- Assert `NO TRADE` changes only local presentation state and performs no HTTP
  mutation.
- Assert context changes clear the presentation decision.
- Run the full Angular test suite, production build, formatting, and
  `git diff --check`.
- Perform authenticated desktop and narrow-mobile browser validation where the
  local environment permits.

## Approval State

This Repository Analysis and Story are ready for human approval. Implementation
must not begin until the scope and acceptance criteria are explicitly approved.
