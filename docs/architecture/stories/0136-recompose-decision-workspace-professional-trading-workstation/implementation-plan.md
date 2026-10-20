# Implementation Plan - Story 0136

## Status

Completed - approved by human.

## Implementation Decision

Implement the full bounded frontend composition refactor on top of merged Story
0135. Keep `DecisionWorkspace` as the stream and workflow coordinator while
extracting only presentation regions with coherent input/output contracts.

No backend, Risk, execution, persistence, global state, or stream ownership
change is planned.

## Target Component Tree

```text
DecisionWorkspace
├── WorkspaceContextStrip
└── workstation-shell
    ├── MarketSelectorRail
    │   └── DisclosureSection
    ├── MarketInvestigationColumn
    │   ├── MarketQuotePanel
    │   ├── chart panel wrapper
    │   │   └── MarketChartComponent
    │   ├── TrendContextPanel
    │   │   └── DisclosureSection for detailed evidence
    │   └── OrderFlowPanel
    │       ├── OrderBookComponent
    │       └── RecentTradesComponent
    ├── DecisionColumn
    │   └── ActionPanel
    │       └── ManualTradeTicket when explicitly opened
    └── ConstraintsPanel
```

The chart remains the existing component. A separate chart wrapper component is
not introduced unless the final template demonstrates a focused lifecycle or
test boundary that justifies it.

## Parent and Child Contracts

### `DecisionWorkspace`

Retain ownership of:

- `accounts$`, `context$`, `marketView$`, `trendContext$`;
- ticker, OHLC, Order Book, and Recent Trades streams;
- explicit subscribe/unsubscribe bookkeeping;
- route query parameters and Opportunity loading/cancellation;
- account and market selection state;
- manual and Opportunity preparation commands.

Resolve shared observables once per visual region in the parent template and
pass resolved view models to children. Child components must not inject
`AccountService`, `DecisionContextService`, `MarketService`,
`MarketDataStreamService`, `OpportunityService`, or `TradePlanService`.

### `MarketSelectorRail`

Inputs:

- account catalogue view;
- selected account ID;
- account-scoped Decision Context view;
- selected market ID;
- normalized search text;
- eligible and unavailable market projections.

Outputs:

- account selection;
- account refresh;
- market search changes;
- eligible market selection;
- unavailable-market disclosure state if needed.

It preserves account loading/error/empty states, search threshold behavior,
eligible count, query restoration, and backend-provided exclusion reasons.

### `MarketQuotePanel`

Inputs:

- loaded `MarketResponse`;
- shared ticker `StreamView<TickerEvent>`;
- parent-derived `MarketFreshness`.

It renders last, bid, ask, spread, optional volume, provider/product, market
status, and local freshness using Story 0135 primitives. It performs no ticker
subscription and does not derive tradability.

### `TrendContextPanel`

Inputs:

- existing `DecisionWorkspaceTrendContextView` projection.

It keeps the current operational status, assessment validity, direction, regime,
phase, attention, alignment, BIAS/SETUP/TRIGGER summaries, historical state,
contradictions, invalidations, exclusions, findings, diagnostics, and source
references. The summary is always visible when loaded; secondary evidence is
inside an accessible `DisclosureSection`.

It preserves explicit `Analytical evidence only` and `Not Risk approval / Not
execution authorization` wording. It does not calculate analytical values.

### `ActionPanel`

Inputs:

- selected account and market;
- current ticker-derived manual reference price;
- current Opportunity and Opportunity error/preparing state;
- manual-ticket open state.

Outputs:

- open manual ticket;
- close manual ticket;
- prepare from Opportunity.

The existing `ManualTradeTicket` remains the form and continues to own its
existing Trade Plan API call and navigation behavior. `ActionPanel` owns only
presentation and event forwarding; it does not create Risk or execution state.

### `OrderFlowPanel`

Inputs:

- shared Order Book `StreamView<OrderBookSnapshot>`;
- shared Recent Trades `StreamView<RecentTradesSnapshot>`;
- selected Order Book depth and existing depth options;
- parent callbacks for depth changes.

Desktop renders both child components in one bounded evidence region. The
approximately 1024px fallback may stack them; Story C can introduce final narrow
tabs only if needed. No inactive panel subscription is created by this
component.

### `ConstraintsPanel`

Inputs:

- authoritative `MarketResponse.marketConstraints`;
- authoritative closure reason.

It renders a compact definition-list/table or disclosure using `MetricValue` and
preserves all existing constraint fields. It does not format or round values in
ways that affect Trade Plan or Risk inputs.

## Layout Implementation

1. Replace the large page header with a modest semantic H1 and minimal guidance
   retained for unresolved account state.
2. Keep `WorkspaceContextStrip` directly below the page heading.
3. Wrap the selected-market region in a workstation CSS Grid with:
   - a selector rail capped around 240-280px;
   - an investigation column using `minmax(0, 1fr)`;
   - a decision column capped around 320-380px.
4. Place quote and chart at the top of the investigation column.
5. Place Trend Context summary and Order Flow below the chart.
6. Place Opportunity/manual ActionPanel in the decision column.
7. Place constraints/supporting evidence below the investigation region or in a
   compact full-width supporting row.
8. Use a two-column or stacked fallback at approximately 1024px and a single
   column below the existing narrow breakpoint without introducing a desktop-only
   overflow failure.

Use Story 0135 workstation variables and primitives. Remove redundant large
card boundaries, Step 1/2/3 visual headings, the large selected-market header,
and duplicated account/market identity only after equivalent semantic content is
present in the strip, selector, quote, or action region.

## Trend Context Migration

Move the current Trend Context markup without changing read-model access:

- preserve loading and unavailable panel states;
- keep the loaded summary row visible;
- move provenance, role details, levels, findings, contradictions,
  invalidations, diagnostics, source references, and historical provenance into
  one or more disclosures;
- preserve `data-testid` hooks where they represent semantic regression
  coverage, updating selectors only when the component boundary requires it.

Any extraction that would require a new business view model will stop and be
reduced to a presentation-only input projection.

## Existing Component Adjustments

- Reduce Order Book CSS height from 42rem to a bounded panel height around
  280-360px on desktop, with an explicit safe fallback at narrower widths.
- Keep Recent Trades semantic table-like DOM and existing mobile column behavior.
- Keep Market Chart component and ResizeObserver cleanup unchanged; only its
  container sizing changes.
- Reuse `WorkspacePanel`, `WorkspacePanelHeader`, `StatusBadge`,
  `FreshnessIndicator`, `MetricValue`, and `DisclosureSection`.

## Files Expected to Change

- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.scss`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts`
- `trading-os-web/src/app/features/markets/order-book-component/order-book-component.scss`
- new presentation components under
  `trading-os-web/src/app/features/decision-workspace/` for the selector rail,
  quote, Trend Context, action, Order Flow, and constraints regions;
- focused tests for each extracted component where meaningful.

No file under backend services, Risk, execution, S1, Research, or unrelated
frontend features is expected to change.

## Validation Sequence

1. Add focused component tests for all extracted state and action contracts.
2. Run the focused Decision Workspace and extracted-component suites.
3. Run complete `npm run test:ci`.
4. Run `npm run build` and record pre-existing budget warnings accurately.
5. Run Prettier check and `git diff --check`.
6. Start/use the local frontend runtime and inspect 1920px, 1440px, 1024px,
   and narrow layouts.
7. Walk through account-first and market-first flows; exercise Opportunity-first
   if runtime data exists.
8. Compare browser network activity for equivalent loaded-market state and verify
   no additional HTTP/WebSocket activity or stale subscriptions.
9. Document Story C handoff items without expanding this Story.

## Stop Conditions

Stop and report if any implementation requires:

- a backend field or endpoint;
- a new authoritative Trade Plan/Risk projection;
- moving stream ownership to a child component;
- global frontend state or cross-feature stream caching;
- changing account/market/Opportunity route semantics;
- changing Risk, execution, eligibility, or authorization meaning.
