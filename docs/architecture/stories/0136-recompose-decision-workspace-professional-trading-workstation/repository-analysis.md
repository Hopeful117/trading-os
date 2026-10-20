# Repository Analysis - Story 0136

## Status

Completed - approved by human; implementation planning authorized.

## Repository Baseline

- Branch: `main`
- HEAD: `29a29f4f` (`Merge pull request #150 from Hopeful117/story/0135-workstation-presentation-primitives`)
- Story A implementation: `af88e875`, merged into `main`.
- Working tree: four pre-existing untracked investigation reports; no tracked
  production changes.
- Next valid Story identifier: `0136`.

The untracked reports are preserved and are outside this Story:

- `docs/architecture/reports/professional-decision-workspace-density-panel-architecture.md`
- `docs/architecture/reports/trading-os-next-product-capability-investigation.md`
- `docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`
- `docs/architecture/reports/v1-functional-audit.md`

## Authoritative Context

The governing density report defines the three-Story UI chantier. Story A is
complete and established the workstation primitives and compact
`WorkspaceContextStrip`. Story B is the desktop panel/grid recomposition. Story
C owns final responsive, accessibility, and stream validation polish.

The current repository confirms the report's assumptions with one update: the
Story A component is named `WorkspaceContextStrip`, and its primitives live under
`trading-os-web/src/app/shared/workstation`.

DevLog applicability was checked and failed with provider quota exhaustion
(`HTTP 429`, `credit_balance_exhausted`). No DevLog evidence is used as authority.

## Current Architecture

`DecisionWorkspace` remains the single orchestration boundary. It currently owns:

- account catalogue loading and refresh;
- account selection and URL restoration;
- Decision Context resolution and eligible market projections;
- market facts and tradability gating;
- Trend Context loading;
- Opportunity request/cancellation and route reconciliation;
- ticker, OHLC, Order Book, and Recent Trades stream activation/teardown;
- manual Trade Plan preparation and Opportunity preparation navigation.

The current template is 934 lines and the component class is 852 lines. Its
selected-market path is sequential:

1. page header;
2. Story A context strip;
3. account selection;
4. account summary and market search;
5. eligibility disclosure;
6. market header;
7. full Trend Context evidence;
8. manual/Opportunity action;
9. ticker;
10. chart;
11. Order Book and Recent Trades;
12. market constraints.

The current stylesheet is 632 lines and uses large generic cards, a 440px chart,
and a 42rem Order Book. This is the primary density problem.

## Data and Stream Authority

The existing dependency chain is authoritative and must remain unchanged:

```text
Account
  -> DecisionContext
  -> eligible market
  -> MarketResponse/tradability
  -> Trend Context, ticker, OHLC, Order Book, Recent Trades
```

The parent exposes shared `shareReplay({ bufferSize: 1, refCount: true })`
observables. Explicit backend subscribe/unsubscribe bookkeeping remains in the
parent. New presentation components must receive values or shared view models;
they must not inject services or independently resolve data.

No Trade Plan, Risk, or execution read model exists in Decision Workspace.
PlanPage remains authoritative after preparation.

## Proposed Component Boundaries

The smallest coherent extraction for this Story is:

- `DecisionWorkspace` remains the coordinator and composes the layout;
- `MarketSelectorRail` receives account/context state, search state, and emits
  existing account/market/refresh actions;
- `MarketQuotePanel` receives market facts and shared ticker state;
- `TrendContextPanel` receives the existing Trend Context view and emits only
  disclosure state if needed;
- `ActionPanel` receives current Opportunity/manual-action state and hosts the
  existing manual ticket when open;
- `OrderFlowPanel` composes existing Order Book and Recent Trades inputs without
  owning subscriptions;
- `ConstraintsPanel` presents existing market constraints and closure notice.

These are presentation boundaries, not new domain services or stores. The exact
names may adapt to repository conventions, but no dozens-of-wrappers extraction
is justified.

## Layout Strategy

Use a workstation-scoped CSS Grid in `DecisionWorkspace`:

- persistent `WorkspaceContextStrip` row;
- unresolved state: compact selector/context region;
- active market: selector rail, investigation column, and bounded decision column;
- investigation column: quote/chart, Trend summary, and Order Flow;
- decision column: Opportunity/action and future-safe empty space only, with no
  fabricated Plan/Risk panel;
- supporting constraints/evidence below or as disclosures.

Use stable decision-column width around 320-380px on desktop, bounded chart and
Order Book heights, and a safe one-column/stacked fallback around 1024px. Story C
will refine mobile tabs, focus order, and final breakpoints.

## Semantic Preservation

The recomposition must preserve:

- account-first, market-first, and Opportunity-first workflows;
- pending market restoration after account selection;
- eligibility and tradability distinctions;
- explicit Trend Context statuses including historical/unavailable states;
- local market/stream error semantics;
- manual Trade Plan and Opportunity preparation commands;
- PlanPage navigation and human authorization boundary;
- visible LONG/SHORT and neutral Opportunity score semantics.

No values may be inferred merely to fill a panel. Missing data remains
`UNKNOWN`, `UNAVAILABLE`, `NONE`, `MISSING`, `HISTORICAL`, or the existing state
wording as applicable.

## Validation Impact

Expected focused coverage includes:

- selector account/market gating, search, eligibility disclosure, and emitted
  actions;
- quote loading/error/live/freshness/tradability states;
- Trend Context summary, disclosure, historical, unavailable, and authority text;
- action and Opportunity states without Risk semantics;
- Order Flow local states and existing child inputs;
- constraints visibility and disclosure;
- unchanged Decision Workspace stream activation/teardown and route tests.

Runtime validation must compare the equivalent loaded-market state for request and
subscription counts. Browser inspection is required where the local runtime
permits it; otherwise the limitation must be explicit.

## Decision

**A - IMPLEMENT.** The Story can be implemented safely as a frontend-only
composition refactor. The repository supports the required boundaries and no
missing backend or authoritative contract is needed.

No ADR is required unless implementation unexpectedly introduces global durable
state, cross-feature stream caching, embedded authoritative Plan/Risk state, or
workflow semantic changes. If any of those become necessary, implementation must
stop and report the contradiction.
