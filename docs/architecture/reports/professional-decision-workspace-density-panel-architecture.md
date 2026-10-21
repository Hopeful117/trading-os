# Investigation - Professional Decision Workspace Density and Panel Architecture

**Date:** 2026-10-20  
**Branch:** `main`  
**HEAD:** `436d2e438f74f0e28b2cdadaf67ff18d7ae74a10`  
**Scope:** Frontend UX and Angular composition design only  
**Intended repository change:** This report only

## 1. Starting State

The working tree contains three unrelated untracked investigation reports:

- `docs/architecture/reports/trading-os-next-product-capability-investigation.md`
- `docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`
- `docs/architecture/reports/v1-functional-audit.md`

They were preserved unchanged. No production files, Story 0134 files, backend
semantics, Risk code, execution code, S1 artifacts, or protected audit files were
modified.

Recent merged work confirms the current baseline:

- Story 0132 established typed domain navigation.
- Story 0133 established research integrity observability.
- Story 0134 established the account-scoped workspace context slice.
- HEAD is the merge of Story 0134.

DevLog applicability was checked for prior workspace decisions and Story 0134
constraints. The Story Agent was unavailable because the configured provider
returned HTTP 429 for exhausted quota. Repository, accepted ADR, Story, and
existing report evidence are therefore authoritative for this investigation.

## 2. Governing Artifacts

The governing UX decision is recorded in
`docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`:
use a contextual workspace shell around Decision Workspace, not a Bloomberg
clone, dashboard replacement, global store, or customizable terminal.

Story 0134 is the current implementation boundary. It requires a compact
authoritative context surface for account, market, tradability, freshness,
workflow stage, and next action while preserving market-first,
opportunity-first, account-first, and position-first semantics.

Relevant architectural constraints are ADR-001, ADR-014, ADR-031, ADR-043, and
ADR-047. The practical rules are:

- Angular composes authoritative projections and explicit user intent only.
- Angular must not calculate eligibility, Risk approval, execution
  authorization, Opportunity validity, or Trade Plan validity.
- Risk remains deterministic and fail-closed.
- `Execute Trade` remains a separate explicit human authorization.
- Unknown, stale, unavailable, blocked, and error states remain distinct.
- Market streams open only after account eligibility and tradable market
  resolution.

## 3. Current Decision Workspace Architecture

### 3.1 Component tree today

```text
DecisionWorkspace
├── WorkspaceContext
├── account selection and account loading states
├── account-scoped DecisionContext selection
├── market search and eligible-market picker
├── unavailable-market disclosure
├── selected market header and tradability state
├── Trend Context markup
├── ManualTradeTicket (conditional)
├── opportunity preparation action (conditional)
├── ticker markup
├── MarketChartComponent
├── OrderBookComponent
├── RecentTradesComponent
└── market constraints markup
```

The feature is intentionally standalone, but the parent remains very large:

- `decision-workspace.ts`: 847 lines;
- `decision-workspace.html`: 939 lines;
- `decision-workspace.scss`: 632 lines;
- `decision-workspace.spec.ts`: 911 lines.

`WorkspaceContext` is a meaningful child boundary with a typed
`WorkspaceContextView`. The chart, order book, recent trades, and manual ticket
are also meaningful existing boundaries. Trend Context, account/market
selection, Opportunity presentation, ticker presentation, and constraints are
still embedded in the parent.

### 3.2 Current visual/data blocks

| Current block | Purpose | Source | Current position | Importance | Current treatment |
|---|---|---|---|---|---|
| Page header | Names the page and instructs account-first use | Static text | Top | Orientation | Large header with explanatory paragraph |
| WorkspaceContext | Account, market, tradability, freshness, stage, next action | Composed from account/context/market/ticker/Opportunity streams | Immediately below header | Tier 1 | Large bordered gradient card |
| Account card | Load, refresh, and select accounts | `AccountService.getAccounts()` | Before account context | Tier 1 before selection | Card with large section heading |
| Select-account state | Prevents market work without account | `context$` state | After account card | Tier 1 | Full-width state card |
| Account summary | Currency, Risk profile, planning profile | `DecisionContextResponse.account` | After context resolves | Tier 1/3 | Card in two-column context grid |
| Market picker | Search and select eligible market | `DecisionContextResponse.markets` | Beside account summary | Tier 1 | Card with search and button grid |
| Unavailable markets | Explain excluded catalogue entries | `DecisionContextResponse.markets` | Below picker | Tier 3 | Collapsed disclosure |
| Market header | Symbol, provider, product, state, timestamp | `MarketResponse` | Start of selected-market area | Tier 1 | Large bordered card |
| Trend Context | Deterministic multi-timeframe evidence, provenance, findings, diagnostics | `TrendContextService` | First large content section | Tier 2 | Large nested card with many subcards/lists |
| Manual action area | Open manual ticket or prepare from Opportunity | `TradePlanService`, route Opportunity | After Trend Context | Tier 1 when acting | Action card |
| ManualTradeTicket | Compose MANUAL Trade Plan | Existing ticket inputs and selected context | Conditional in same flow | Tier 1 when open | Large form card |
| Opportunity context | Direction, scenario, explanation, preparation action | `OpportunityService` and route query | Inside trading action area | Tier 1 when present | Inline nested card |
| Ticker | Last, bid, ask, spread, volume | Ticker WebSocket | After actions | Tier 1 | Five metric cards |
| Chart | Historical OHLC plus live candle | HTTP OHLC + OHLC WebSocket | After ticker | Tier 2 | 440px chart card |
| Order book | Spread, volumes, imbalance, levels | Order-book WebSocket | Lower two-column grid | Tier 2 | 42rem child component |
| Recent trades | Time, side, price, quantity, notional | Trades WebSocket | Lower two-column grid | Tier 2 | Scrollable compact table |
| Market information | Minimums, tick/quantity/price precision, closure reason | `MarketResponse` | Bottom | Tier 3 | Five metric cards and notice |
| Trade Plan/Risk | Proposal, lifecycle, Risk decision, execution authorization | `PlanPage` and `TradePlanService` | Not present in current workspace | Tier 1 after preparation | Separate route/page, not a current panel |

### 3.3 What is always visible

Before a market is selected, the page always shows the large header,
`WorkspaceContext`, account card, and then account-scoped context states. After a
market is selected, the page always shows the market header, Trend Context
state, trading actions, ticker, chart, order book, recent trades, and
constraints, subject to their local state branches.

The current workspace does not keep a compact decision summary pinned while the
user scans lower evidence. The account and market facts are repeated across
the context card, market header, action area, and manual ticket.

## 4. Actual Data Dependency Map

```text
AccountService.getAccounts()
  -> accounts$
  -> account selector
  -> WorkspaceContext account state

selected account id
  -> DecisionContextService.resolve(accountId)
  -> context$
     ├── authoritative account projection
     ├── eligible market ids
     └── market catalogue with eligibility reasons

selected market id + eligible market ids
  -> MarketService.findById(marketId)
  -> marketView$
     ├── normalized identity
     ├── Market Data trading status/tradability
     └── market constraints

loaded tradable market
  ├── MarketService.findOhlcHistory(marketId, interval, 200)
  │     -> ohlcHistory$ -> MarketChartComponent
  ├── MarketService.subscribe + MarketDataStreamService.streamTicker(symbol)
  │     -> ticker$
  ├── MarketService.subscribe + MarketDataStreamService.streamOhlc(...)
  │     -> ohlc$ -> MarketChartComponent live candle
  ├── MarketService.subscribe + MarketDataStreamService.streamOrderBook(...)
  │     -> orderBook$ -> OrderBookComponent
  └── MarketService.subscribe + MarketDataStreamService.streamRecentTrades(...)
        -> recentTrades$ -> RecentTradesComponent

loaded market
  -> TrendContextService.findTrendContext(marketId)
  -> trendContext$
     └── deterministic Trend Context evidence

route opportunityId
  -> OpportunityService.findById(opportunityId)
  -> imperative opportunity field + opportunitySubject
  -> WorkspaceContext and opportunity action

selected account + selected market + ticker reference price
  -> ManualTradeTicket
  -> TradePlanService.createManual(...)

Opportunity + selected account
  -> TradePlanService.createFromOpportunity(...)
  -> PlanPage

PlanPage, not DecisionWorkspace
  -> TradePlanService.getPlan / decide / evaluateRisk
  -> Risk decision and explicit execution authorization
```

There is no current `DecisionWorkspace` Risk stream, Trade Plan stream, or
execution stream. A workstation redesign must not pretend that the parent owns
those projections. It may provide a stable decision column containing the
existing preparation action and a clear route into `PlanPage`; adding an
embedded plan/risk projection is a separate contract/composition Story.

## 5. Stream Ownership Map

| Stream | Owner | Creation | Teardown/resubscription | Network risk |
|---|---|---|---|---|
| Accounts | `DecisionWorkspace.accounts$` | Account page subscription, refresh subject | `switchMap` on refresh, `shareReplay({refCount:true})` | Safe to reuse in same component; no global cache |
| Decision Context | `DecisionWorkspace.context$` | Selected account subject | `switchMap` cancels prior account resolution | Correct account boundary; keep lazy |
| Market facts | `marketView$` | Context ready + selected market | `switchMap`, rejects ineligible market before HTTP | Must stay upstream of all market panels |
| Trend Context | `trendContext$` | Loaded market | `switchMap` per market; one HTTP projection shared | Keep separate from live sockets |
| OHLC history | `ohlcHistory$` | Loaded market + selected timeframe | `switchMap` per market/timeframe | One HTTP request per change; do not reimplement in chart panel |
| Ticker | `ticker$` | Loaded market | `tickerStream()` subscribes backend then WebSocket; explicit unsubscribe | One socket because stream is shared; preserve centralized owner |
| OHLC live | `ohlc$` | Loaded market + timeframe | Explicit backend unsubscribe and WebSocket teardown | Timeframe changes intentionally replace stream |
| Order book | `orderBook$` | Loaded market + depth | Explicit unsubscribe on market/depth changes | High-frequency; keep mounted only when panel is active |
| Recent trades | `recentTrades$` | Loaded market | Explicit unsubscribe on market changes | High-frequency; bounded child table |
| Opportunity | Imperative request plus `opportunitySubject` | Query parameter changes | Cancel subject and `takeUntilDestroyed` | Do not move to a second independent loader |
| WorkspaceContext projection | `workspaceContext$` | Combines existing shared streams | `shareReplay` | Presentation-only; no new HTTP |

The current repeated template bindings such as multiple `ticker$ | async` and
`orderBook$ | async` usages do not independently create WebSockets because the
public streams use `shareReplay({bufferSize: 1, refCount: true})`. Extraction
must preserve this property. A panel should receive a view model or the shared
observable from the parent, not call `MarketDataStreamService` itself.

The more significant risk is not current async-pipe duplication. It is the
imperative subscription bookkeeping in `active*Subscription`, explicit backend
subscribe/unsubscribe calls, and `clearActiveSubscriptions()`. Keep one stream
coordinator in `DecisionWorkspace` or extract it as one focused presentation
facade; do not let four new panels each own lifecycle cleanup.

## 6. First Viewport Audit

The shell gives the route content `24px` padding and a height of
`calc(100vh - 64px)`. Decision Workspace adds another `2rem` padding. At a
1440x900-class viewport, approximately 56px of vertical space is consumed
before the feature content begins.

The current first viewport generally contains:

1. The `Decision context` eyebrow, `Market Decision Workspace` H1, and
   explanatory sentence.
2. The full `WorkspaceContext` gradient card.
3. The full account-selection card, including its `Step 1` heading and refresh
   button.
4. After account selection, part or all of the account summary and market picker.

The selected market, price, chart, Trend Context, Opportunity action, Trade
Plan action, and Risk state are below the fold in the normal selected-market
journey. This fails the primary objective even though the required data exists.

The main measured causes are visible in source:

- page padding is `2rem` in `decision-workspace.scss`, on top of shell padding;
- the page header has a large semantic block before context;
- account context is represented once by `WorkspaceContext` and again by the
  account card and selected-account summary;
- market selection is a full card beside another full account card;
- selected market identity is repeated in `WorkspaceContext`, market header,
  ticker section, and manual ticket;
- Trend Context is rendered before ticker and chart and contains many nested
  cards and long provenance/diagnostic lists;
- the chart has a fixed 440px height;
- the order book has a fixed 42rem height;
- generic cards use 1.25rem padding and rounded borders almost everywhere.

## 7. Current Density Problems

- The page is sequential rather than simultaneous: selection, analysis, action,
  ticker, chart, order flow, and constraints form a vertical narrative.
- The highest-value live price is below the account/context setup.
- Trend Context is both an executive summary and a deep evidence dump; these
  should not have equal visual weight.
- Account, market, provider, state, and timestamp repeat in multiple sections.
- The Opportunity action is visually subordinate to the large Trend Context
  block despite being a direct workflow input.
- There is no persistent Trade Plan/Risk decision rail in this route.
- Market microstructure is technically dense but receives the same outer card
  treatment as low-priority constraints.
- The order book's 42rem height is incompatible with a first workstation
  viewport and forces excessive vertical scrolling.
- The chart's 440px height is appropriate as a standalone detail view but too
  dominant when combined with Trend Context, plan action, and order flow.
- Static headings consume space without adding state or decision information.
- Current card styling gives the page a generic SaaS dashboard appearance: each
  group is an isolated rounded rectangle with a shadow/border treatment.
- The full eligible market catalogue is intentionally hidden until search, which
  is good for scale, but the selection control remains visually large.
- Loading states often appear as full cards; the redesign should reserve panel
  geometry and use compact local state rows instead.

## 8. Strengths To Preserve

- Account selection precedes account-scoped eligibility resolution.
- Query parameters preserve account, market, and Opportunity context.
- Market-first entry can select a market before account context is required.
- Ineligible and not-tradable are distinct states.
- The WorkspaceContext explicitly separates account, market, tradability,
  freshness, Opportunity, workflow stage, and next action.
- Trend Context explicitly says it is not Risk approval or execution
  authorization.
- Local panel errors do not blank the whole selected-market area.
- Active market streams are opened only after a loaded tradable market.
- Existing chart, order book, and recent-trades components already have useful
  isolated responsibilities and `OnPush` change detection where appropriate.
- Recent trades and order book use compact tabular numeric layouts.
- The manual ticket preserves selected account and market and does not duplicate
  its selectors.
- Existing tests cover account gating, URL restoration, stream opening, cleanup,
  failure truthfulness, and Opportunity context preservation.

## 9. Information Tiers

### Tier 1 - Always visible in the active market state

- account name and mode if authoritative;
- selected market symbol and product/provider;
- market tradability and trading status;
- primary price, bid, ask, spread;
- local market freshness and timestamp;
- current workflow stage and next safe action;
- active Opportunity summary when present;
- Trade Plan/Risk status when the user is already on a plan context, but only
  through an authoritative plan projection if one is added later.

### Tier 2 - Primary investigation

- chart and timeframe;
- compact Trend Context summary;
- Opportunity direction/type/timeframe/score/validity/rationale;
- order book summary and bounded levels;
- recent trades table.

### Tier 3 - Supporting evidence

- Trend Context role details, levels, protected levels, findings,
  contradictions, exclusions, and source references;
- Opportunity provenance identifiers and full explanation;
- market constraints;
- account profile/version metadata;
- detailed timestamps.

### Tier 4 - Diagnostics

- analysis execution IDs;
- lineage and content digests;
- raw source references;
- Trend Context diagnostics;
- full unavailable/reconciliation details.

Tier 4 remains available through disclosures or detail routes. It must not
compete with the current price, tradability, Opportunity, or action.

## 10. Target Panel Architecture

The target is a fixed, opinionated workstation composition rather than a widget
system.

### Proposed panels

| Panel | Priority | Reason |
|---|---|---|
| `WorkspaceContextStrip` | ALWAYS VISIBLE | Establishes account, market, state, freshness, stage, and next action |
| `MarketSelectorRail` | VISIBLE WHEN NO MARKET | Account resolution and focused market search |
| `MarketQuotePanel` | ALWAYS VISIBLE when market loaded | Primary price and tradability facts |
| `MarketChartPanel` | ALWAYS VISIBLE when market loaded | Main investigation anchor |
| `TrendContextPanel` | VISIBLE WHEN RELEVANT | Deterministic analytical summary; details disclosed |
| `OpportunityPanel` | VISIBLE WHEN PRESENT | Makes the current candidate actionable without implying approval |
| `ActionPanel` | ALWAYS VISIBLE when market loaded | Manual preparation and Opportunity preparation |
| `OrderFlowPanel` | VISIBLE WHEN RELEVANT | Order book and recent trades together as market microstructure |
| `ConstraintsPanel` | COLLAPSIBLE | Needed for order preparation, not continuous focus |
| `EvidenceDetailsPanel` | SECONDARY | Provenance, diagnostics, historical assessment, full findings |
| `TradePlanPanel` | DEFERRED EMBEDDED PROJECTION | Current plan state lives on `PlanPage`; reserve a decision rail slot without duplicating it |
| `RiskPanel` | DEFERRED EMBEDDED PROJECTION | Risk remains authoritative in Trading Core/PlanPage; never derive it from Opportunity |

The first UI chantier should implement the visual slots and action composition
without fabricating a Plan/Risk read model. A later Post-trade Decision History
feature can use the same `WorkspacePanel`, metric, status, and evidence language.

## 11. Recommended Layouts

### 11.1 Standard desktop, approximately 1440px

```text
┌────────────────────────────────────────────────────────────────────────────┐
│ ACCOUNT  MARKET  TRADABLE  PRICE/BID/ASK  FRESHNESS  STAGE  NEXT ACTION   │
├───────────────────────┬───────────────────────────────────┬────────────────┤
│ MARKET SELECTOR       │ CHART + QUOTE                     │ ACTION /       │
│ account switch        │ primary price strip              │ OPPORTUNITY    │
│ market search         │ candlestick chart                │ Prepare plan   │
│ eligible count        │ timeframe controls               │ Manual action  │
├───────────────────────┼───────────────────────────┬───────┴────────────────┤
│ TREND CONTEXT         │ ORDER BOOK                │ DECISION SLOT          │
│ summary + freshness   │ compact levels           │ reserved for plan/risk │
│ BIAS / SETUP / TRIGGER│ spread / imbalance       │ current action state   │
│ disclosure for detail│                           │ no fake Risk state     │
├───────────────────────┴───────────────────────────┼────────────────────────┤
│ RECENT TRADES + MARKET CONSTRAINTS disclosure      │ EVIDENCE disclosure   │
└────────────────────────────────────────────────────┴────────────────────────┘
```

At 1440px, the selector is a narrow left rail only while account or market
selection is unresolved. Once a market is loaded, the chart and action rail
remain visible together. Trend Context is compressed to summary values, with
long findings and provenance behind disclosure.

### 11.2 Wide desktop, approximately 1920px

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ CONTEXT STRIP: ACCOUNT | MARKET | STATUS | LAST/BID/ASK | FRESHNESS | STAGE | ACTION    │
├─────────────────────┬─────────────────────────────────────────┬────────────────────────┤
│ SELECTOR / CONTEXT  │ CHART                                   │ DECISION COLUMN        │
│ account             │ larger chart, same bounded height       │ Opportunity            │
│ market search       │ quote metrics aligned above             │ Trade preparation      │
│ eligibility         │ no stretched empty whitespace           │ Plan/Risk slot         │
├─────────────────────┴─────────────────────────────┬───────────┴────────────────────────┤
│ TREND SUMMARY + ORDER BOOK + RECENT TRADES        │ CONSTRAINTS + EVIDENCE             │
│ three compact evidence panels                     │ disclosures, not dominant          │
└───────────────────────────────────────────────────┴────────────────────────────────────┘
```

Wide screens should increase concurrency and chart width, not inflate every
panel. The decision column remains a stable maximum width around 320-380px.
Order flow can sit beside Trend Context because both are investigation
evidence, while the decision column stays visually distinct.

### 11.3 Narrow desktop/tablet, approximately 1024px

```text
┌──────────────────────────────────────────────────────────────┐
│ COMPACT CONTEXT STRIP: ACCOUNT / MARKET / STATUS / ACTION   │
├──────────────────────────────────────────────────────────────┤
│ SELECTOR + QUOTE                                             │
├──────────────────────────────────────────────────────────────┤
│ CHART                                                        │
├───────────────────────────────┬──────────────────────────────┤
│ OPPORTUNITY / ACTION           │ TREND SUMMARY                │
├───────────────────────────────┴──────────────────────────────┤
│ ORDER FLOW: tabs Order Book | Recent Trades                  │
├──────────────────────────────────────────────────────────────┤
│ CONSTRAINTS / EVIDENCE disclosure                            │
└──────────────────────────────────────────────────────────────┘
```

At this width, Order Book and Recent Trades should share a tabbed presentation
inside one `OrderFlowPanel` only if both remain mounted-free when inactive. The
default should show Order Book because it is more directly relevant to current
tradability and execution context; switching to Recent Trades is explicit.

### 11.4 Mobile degradation

Mobile is not a trading workstation. Preserve one active task at a time:

1. compact context strip with account, market, status, and next action;
2. quote summary;
3. chart;
4. Opportunity and action;
5. Trend Context summary;
6. Order Book and Recent Trades as explicit tabs or disclosures;
7. constraints and diagnostics as disclosures.

The account and market context must remain immediately accessible, but no
desktop rail is forced into a narrow viewport. Risk and execution actions, when
present on a future plan projection, remain in the reading order after the plan
facts and before secondary evidence.

## 12. Panel Recommendations

### Context Strip

Keep Story 0134's `WorkspaceContextView` and evolve its presentation into a
single compact horizontal strip. It should show:

- account name and base currency;
- market symbol and provider/product;
- tradability and trading status;
- primary price with local ticker freshness;
- workflow stage;
- next safe action.

Move detailed Risk/planning profile versions, long explanatory text, and full
Opportunity provenance out of the strip. Do not introduce a universal `LIVE`
badge; retain `LIVE`, `RECENT`, `STALE`, and `UNAVAILABLE` as the current
projection allows.

### Chart

The chart is the primary visual anchor after quote context, but not the whole
page. Keep `MarketChartComponent` and lightweight-charts. Wrap it in a panel
with a responsive height:

- 360-420px at standard desktop;
- 440-500px at wide desktop, only when the surrounding grid has room;
- 300-360px at narrow width;
- 280-320px on mobile.

Do not overlay Opportunity, Trend Context, or actions over candles. Keep
timeframe controls in the panel header and preserve the chart's ResizeObserver
cleanup. A chart with no market remains a compact select-market state, not a
large empty chart area.

### Trend Context

Keep it visibly deterministic and separate from Risk. The primary panel should
show:

- operational status and assessment validity;
- direction, regime, phase, attention;
- alignment;
- BIAS/SETUP/TRIGGER freshness and direction summary;
- a compact contradiction/invalidation warning when present.

Move role grids, confirmed swings, structural levels, source references,
diagnostics, findings, and historical assessment into a disclosure within the
same panel. Preserve explicit wording such as `Analytical evidence only` and
`Not Risk approval / Not execution authorization`.

The panel must display `HISTORICAL`, `EXPIRED`, `NONE`, `MISSING`, and
`UNAVAILABLE` honestly. It must not collapse a historical assessment into a
current positive signal.

### Opportunity

When present, make Opportunity a compact decision-input panel in the right
column. Show:

- instrument and direction;
- type and timeframe;
- score with a neutral label such as `Opportunity score`, never `approval`;
- status and validity window;
- rationale excerpt;
- origin and strategy/provenance availability;
- `Prepare from opportunity` action.

The Opportunity panel should be absent or compactly state `No active Opportunity
for this context` when none exists. It must not turn score into Risk state or
execution readiness.

### Order Book

Keep the existing `OrderBookComponent`, but reduce its fixed 42rem height to a
bounded panel height appropriate to the current grid, approximately 280-360px
on desktop. Keep summary metrics, spread, best bid/ask, asks, and bids. Use
tabular numeric alignment and compact rows. Do not remove levels merely to make
the page look simpler.

Order Book should be visible beside Trend Context or below the chart on desktop,
and become one tab of `OrderFlowPanel` at narrow widths. It remains local to the
selected market and should fail locally.

### Recent Trades

Keep `RecentTradesComponent` as a semantic compact table. On desktop it should
sit beside Order Book or below the chart. On narrow layouts it becomes the
second Order Flow tab. Keep time, side, price, quantity, and notional; hide only
the lowest-value column at mobile width with an accessible table caption or
labelled alternative.

Recent Trades are evidence of flow, not a recommendation. Buy/sell color may
remain as a secondary cue, but the Side text remains visible.

### Trade Plan

The current workspace has no Trade Plan read model. The existing PlanPage owns
proposal facts, acceptance, Risk evaluation, execution readiness, and execution
state. The UI chantier should therefore:

- reserve the right-side decision column;
- keep `Prepare manual trade` and `Prepare from opportunity` there;
- show clear transition text that preparation opens the authoritative Trade Plan
  surface;
- preserve the existing PlanPage route and lifecycle;
- avoid copying plan facts into a frontend-only workspace state.

A later Story may embed a read-only Trade Plan projection if the owning contract
supports it. That is not part of this visual-only refactor.

### Risk

Risk is not currently available in Decision Workspace. The future decision
column must reserve a clearly labelled Risk region, but its first implementation
must not render Opportunity score, Trend Attention, or market tradability as
Risk approval. Any embedded Risk panel must consume the existing persisted
`RiskDecisionResponse` and preserve `APPROVED`, `APPROVED_WITH_WARNINGS`, and
`REJECTED` plus reasons/warnings.

`Execute Trade` remains separate and visually stronger than any analytical
status. Risk state uses text, status label, and semantic border/icon treatment;
color alone is prohibited.

### Market Constraints

Convert the current five metric cards into a compact definition-list row or
collapsible `ConstraintsPanel` near the action area. Keep minimum order size,
minimum cost, tick size, quantity precision, and price precision visible when a
manual or Opportunity plan is being prepared. Keep closure reason as an amber
or red local notice based on the authoritative market state.

## 13. Next Action

Keep the existing `nextAction` presentation projection, but move it into the
right side of the context strip and repeat it as the primary action label only
where a real existing command is available.

The supported sequence is:

```text
Select an account
  -> Choose an eligible market
  -> Review deterministic evidence
  -> Prepare a MANUAL or OPPORTUNITY Trade Plan
  -> Open the authoritative Trade Plan page
```

After the user is on PlanPage, the existing lifecycle remains authoritative:

```text
Accept Trade Plan
  -> Evaluate Risk
  -> Execute Trade (separate explicit authorization)
```

The workspace must not display `Evaluate Risk` or `Execute Trade` as active
commands unless it has a real authoritative plan state. A visual slot is not a
permission to create a second workflow engine.

## 14. Card Migration Matrix

| Current | Target |
|---|---|
| Large page header | Compact page title line; remove explanatory paragraph after context copy is clear |
| `WorkspaceContext` gradient card | `WorkspaceContextStrip`, shared panel boundary, persistent top strip |
| Large account card | `MarketSelectorRail` account row with compact selector and refresh affordance |
| Account summary card | Compact context-strip metadata; profile versions in supporting disclosure |
| Market picker card | `MarketSelectorRail` with search, eligible count, and compact result table/list |
| Unavailable markets card | Keep as collapsed `EligibilityDetails` disclosure |
| Large selected market header | Merge identity, tradability, price, and freshness into context strip/quote panel |
| Trend Context outer card | `TrendContextPanel` with summary first and evidence disclosure |
| Trend Context metric cards | Compact metric row using `MetricValue`; retain semantic labels |
| Trend Context role cards | Dense role table/rows inside disclosure, not three large floating cards |
| Trend findings/diagnostics lists | `EvidenceDetailsPanel` disclosure with local status |
| Trading actions card | `ActionPanel` in fixed right decision column |
| Opportunity inline card | `OpportunityPanel` in decision column, visible only when present |
| ManualTradeTicket | Keep as a dedicated action form panel; open in decision column or full-width modal-like region within workspace |
| Ticker card grid | `MarketQuotePanel` metric strip with tabular numerals |
| Chart market section | `MarketChartPanel` with bounded responsive height |
| Order book market section | `OrderFlowPanel` child, compact table and summary |
| Recent trades market section | `OrderFlowPanel` child/table, adjacent or tabbed |
| Market secondary grid | CSS Grid workstation region; no generic sequential stack on desktop |
| Market information cards | `ConstraintsPanel` compact row/disclosure |
| Closure notice | Local market status notice in quote/constraints area |
| Full state cards | Stable panel-local loading/empty/error rows with reserved geometry |
| Future Trade Plan/Risk route | Reserved decision column slot; no duplicate projection in first UI Story |

## 15. Table Strategy

Use semantic tables when values are row/column data:

- Order Book levels: price, quantity, total;
- Recent Trades: time, side, price, quantity, notional;
- market search results when more than a few matches are shown;
- constraints when the panel is expanded;
- future Decision History and evidence details.

Use compact headers, `scope` attributes, right-aligned numeric columns, and
horizontal overflow only when the table cannot be meaningfully reduced. Do not
convert Trend Context role state into a visually vague card grid; a compact
label/value table is more scannable.

## 16. Numeric Typography

Use existing system font stacks and apply `font-variant-numeric: tabular-nums`
to prices, quantities, spreads, percentages, counts, and timestamps where
alignment matters. Use a monospace numeric treatment only for dense market-data
tables, matching existing Order Book and Recent Trades behavior.

Recommended hierarchy:

- primary last price: 1.35-1.8rem, strong weight;
- bid/ask/spread: 0.95-1.1rem;
- panel metrics: 0.9-1rem;
- table values: 0.75-0.85rem;
- timestamps and provenance: 0.68-0.75rem;
- labels: uppercase, letter-spaced, 0.65-0.72rem.

Preserve backend precision. Formatting is display-only and must not round values
used by TradePlan or Risk.

## 17. Color and State Semantics

| Meaning | Visual direction | Required text |
|---|---|---|
| Selection/context | restrained blue border/accent | selected, context, active |
| Confirmed/available | muted green accent | tradable, available, approved where authoritative |
| Warning/degraded/stale | amber accent | stale, warning, historical, attention |
| Error/rejected/blocked | red accent | unavailable, rejected, blocked, error |
| Unknown/neutral | slate/gray | UNKNOWN, NONE, not selected |
| Direction | neutral label plus side text | LONG/SHORT; never color-only |

Green must not mean “good trade” and red must not mean “short”. Directional
color remains secondary to explicit LONG/SHORT text. Opportunity score,
Trend Attention, Risk decision, tradability, and execution authorization use
separate labels and boundaries.

## 18. Borders, Surfaces, and Spacing

Replace most individual shadows and large rounded cards with shared panel
boundaries:

- one outer workstation surface;
- 1px subtle separators between panel regions;
- 0.45-0.65rem panel radius;
- one stronger blue boundary for the active decision/action column;
- no shadow on every nested metric.

Suggested spacing tokens for the pilot:

- outer workspace gap: `0.75rem`;
- panel gap: `0.5rem`;
- panel padding: `0.75rem`;
- compact row padding: `0.35rem 0.5rem`;
- primary action group padding: `0.85rem`;
- mobile outer padding: `0.75rem`.

This is dense but calm: content is separated by alignment and borders, not
compressed until labels collide.

## 19. Header Hierarchy and Freshness

The page should retain one visually modest H1, such as `Decision Workspace`,
then make the context strip the dominant orientation element. Remove `Step 1`,
`Step 2`, and `Step 3` as large visual headings; replace them with compact
labels or a small workflow indicator in the context strip.

Freshness is local:

- quote/ticker freshness beside last price;
- chart freshness beside timeframe or history timestamp;
- order-book freshness in Order Flow header;
- recent-trades generation time in its header;
- Trend Context assessment validity beside its summary;
- market-state `lastUpdated` beside tradability.

Do not create one universal `LIVE` status that implies all panels are current.

## 20. Loading, Empty, and Error Strategy

Panel geometry should remain stable. Replace large full-width spinners with:

- compact skeleton/placeholder rows for quote, chart, Trend Context, and order
  flow;
- a local `Loading market context` state while Market Data resolves;
- preserved previous data only where the existing contract explicitly supports
  it, with a stale marker rather than silent replacement;
- concise empty copy such as `Select a market to load market evidence.` and
  `No active Opportunity for this context.`

Errors remain local whenever dependency boundaries allow:

- order-book failure does not remove chart or action panel;
- recent-trades failure does not remove Trend Context;
- Trend Context unavailable does not imply Risk rejection;
- market facts failure prevents streams and keeps the action area blocked;
- account/context failure prevents account-scoped actions and says why.

Never replace unavailable with zero, and never display stale Trend Context as
current. Keep the current test semantics as acceptance criteria.

## 21. Keyboard and Accessibility

The component order must follow decision order:

1. context/account;
2. market search/selection;
3. quote/chart controls;
4. Opportunity/action;
5. Trend Context;
6. Order Flow;
7. constraints/evidence.

Use semantic `header`, `main`, `section`, headings, `label`, `button`, and
`table` elements. Preserve visible `:focus-visible` outlines. Every compact
status must include text, not only a colored border. Disclosure controls must
expose `aria-expanded` and an accessible name. Tables require captions or
labelled containing sections and sensible mobile overflow.

Do not add command-palette shortcuts yet. Avoid DOM order that places a visually
right-side action before account/market context for screen readers. Future
keyboard work can map predictable shortcuts to market search, timeframe, and
panel focus without changing this layout foundation.

## 22. Component Architecture

### 22.1 Target tree

```text
DecisionWorkspace
├── WorkspaceContextStrip
├── DecisionWorkspaceShell
│   ├── MarketSelectorRail
│   │   ├── AccountSelector
│   │   ├── MarketSearch
│   │   └── EligibilityDisclosure
│   ├── MarketInvestigationColumn
│   │   ├── MarketQuotePanel
│   │   ├── MarketChartPanel
│   │   │   └── MarketChartComponent
│   │   ├── TrendContextPanel
│   │   └── OrderFlowPanel
│   │       ├── OrderBookComponent
│   │       └── RecentTradesComponent
│   ├── DecisionColumn
│   │   ├── OpportunityPanel
│   │   ├── ActionPanel
│   │   │   └── ManualTradeTicket (conditional)
│   │   ├── TradePlanSlot (future authoritative projection)
│   │   └── RiskSlot (future authoritative projection)
│   └── SupportingEvidence
│       ├── ConstraintsPanel
│       └── EvidenceDetailsPanel
```

The first implementation need not create every node as a component. Create a
component when it has distinct data projection, meaningful tests, or reusable
visual responsibility. Keep the stream coordinator in the parent or one
dedicated facade; child panels should be input-driven and presentation-only.

### 22.2 Proposed primitives

| Primitive | First uses | Reuse evidence |
|---|---|---|
| `WorkspacePanel` | Quote, Trend, Order Flow, Action, Constraints | Market Detail, Opportunities, Positions, future History |
| `WorkspacePanelHeader` | Every workstation panel | All dense feature panels |
| `WorkspaceContextStrip` | Decision Workspace account/market context | Future Market Detail and Position context |
| `StatusBadge` | tradability, freshness, Opportunity, Risk states | Opportunities, Positions, Dashboard, History |
| `FreshnessIndicator` | quote, chart, order book, trades, Trend Context | Market Detail, Positions, Research status where semantics match |
| `MetricValue` | last/bid/ask/spread, Trend summary, constraints | Dashboard, Positions, Decision History |
| `CompactDataTable` | Order Flow, market matches, evidence | Opportunities, Positions, History, Research |
| `DisclosureSection` | unavailable markets, Trend details, constraints, diagnostics | Research, Opportunity Detail, Position diagnostics |
| `ActionRail` | manual/opportunity preparation and future plan actions | Plan/History follow-up, not generic dashboard actions |

These are visual/presentation primitives, not a design-system rewrite and not a
state-management layer. A one-off `MarketQuotePanel` remains justified because
it owns the semantic grouping of ticker facts and freshness, while generic
`MetricValue` handles its internal presentation.

## 23. Performance and Stream Risks

- Preserve `shareReplay({bufferSize: 1, refCount: true})` on the shared stream
  projections. Do not make each panel call the stream service.
- Keep account and market resolution upstream of all panel activation.
- Mount Order Book and Recent Trades once on desktop. On narrow tabbed layouts,
  use conditional rendering or a shared stream input so hidden inactive panels
  do not create duplicate visual work or sockets.
- Do not render unbounded DOM for order flow. Existing snapshots are bounded by
  requested depth/trades; preserve that contract and use internal overflow.
- Avoid placing a live stream Observable directly in multiple new child inputs
  unless it remains shared. Prefer a parent view model with one async binding
  per panel.
- Keep lightweight-charts `ResizeObserver` and chart teardown in the existing
  chart component. Grid resize must not recreate the chart on every layout
  change.
- Do not eagerly fetch News, Research, positions, or Decision History into this
  page. They are separate domains and future surfaces.
- Keep `WorkspaceContext` a projection. It must not independently resolve
  account, market, or Opportunity data.
- The current repeated async bindings are safe from WebSocket multiplication due
  to sharing, but consolidating them during extraction will reduce change-
  detection work and make the ownership easier to verify.

## 24. Compatibility With Future Surfaces

### Market Detail

Market Detail already contains ticker, chart, Order Book, Recent Trades, and
constraints. It can later adopt `WorkspacePanel`, `WorkspacePanelHeader`,
`MetricValue`, `FreshnessIndicator`, and compact tables without becoming
account-scoped. Its exploratory role remains distinct from Decision Workspace.

### Positions

Positions can adopt the context strip, status badges, metric values, compact
tables, and local action rail. Its broker/PAPER authority and dangerous close
confirmation remain unchanged. Position monitoring must not inherit Trend
Context or Opportunity semantics merely because the visual primitives are
shared.

### Decision History

The target language is specifically compatible with the next product capability:
Opportunity/MANUAL origin, Trade Plan, Risk, execution, position, settlement,
and provenance can each become compact panels or timeline rows. The decision
column slot and evidence disclosure avoid forcing future history into a large
generic card.

### Research

Research can reuse panel, table, status, freshness, and disclosure primitives,
but remains semantically separate. Research readiness must not appear as trading
Risk or Opportunity evidence without an explicit contract.

## 25. Exact Migration Map

```text
Current DecisionWorkspace.page-header
  -> compact DecisionWorkspace heading plus persistent WorkspaceContextStrip

Current WorkspaceContext
  -> WorkspaceContextStrip; retain the same WorkspaceContextView authority

Current account-card
  -> MarketSelectorRail account selector and compact account state row

Current context-grid.account-summary
  -> context-strip metadata plus supporting account-profile disclosure

Current context-grid.markets-summary
  -> MarketSelectorRail market search and eligible result list/table

Current excluded-markets disclosure
  -> EligibilityDisclosure inside MarketSelectorRail

Current market-header
  -> WorkspaceContextStrip market identity/tradability plus MarketQuotePanel

Current trend-context-panel
  -> TrendContextPanel summary region plus EvidenceDetailsPanel disclosure

Current trend-context-summary
  -> compact MetricValue row

Current trend-context-roles
  -> compact role table/rows inside TrendContextPanel details

Current trend-context-list and provenance
  -> EvidenceDetailsPanel; preserve diagnostics and historical labels

Current trading-actions
  -> ActionPanel in the fixed DecisionColumn

Current opportunity-action
  -> OpportunityPanel in DecisionColumn, conditional on active Opportunity

Current ManualTradeTicket
  -> ActionPanel child or expanded DecisionColumn form; preserve inputs and API

Current ticker section
  -> MarketQuotePanel; move primary metrics into first active-market row

Current chart section
  -> MarketChartPanel in MarketInvestigationColumn

Current market-secondary-grid
  -> OrderFlowPanel region with desktop side-by-side and narrow tabs

Current order-book section
  -> OrderFlowPanel order-book view; retain OrderBookComponent input contract

Current recent-trades section
  -> OrderFlowPanel recent-trades view; retain RecentTradesComponent input contract

Current market information section
  -> collapsible ConstraintsPanel near ActionPanel

Current state-card branches
  -> panel-local stable state rows; retain data-testid/state semantics

Future PlanPage plan/risk states
  -> reserved TradePlanSlot/RiskSlot only after authoritative projection Story
```

## 26. Implementation Sequencing

The safest implementation is incremental and preserves the current template
behavior at each step:

1. **Workstation primitives and tokens:** establish panel headers, status,
   freshness, metric, disclosure, table, numeric, focus, and spacing rules in
   the frontend only.
2. **Context strip compression:** evolve `WorkspaceContext` into the compact
   top strip without changing `WorkspaceContextView`, route semantics, or data
   ownership.
3. **Layout shell/grid:** introduce the opinionated desktop CSS Grid and narrow
   responsive regions while leaving existing panel markup functionally intact.
4. **Selector and quote migration:** move account/market selection into the
   selector rail and ticker into `MarketQuotePanel`; remove duplicate large
   identity blocks.
5. **Evidence migration:** migrate chart, Trend Context, Order Book, Recent
   Trades, and constraints one panel at a time. Keep the existing Observables
   and child component inputs.
6. **Decision column migration:** move Opportunity and manual preparation into
   the ActionPanel. Do not add plan/risk business state.
7. **Responsive and accessibility pass:** validate 1440px, 1920px, 1024px,
   and mobile degradation, focus order, semantic headings/tables, local states,
   and no duplicate subscriptions.
8. **Regression/runtime validation:** run focused tests, full Angular tests,
   production build, formatting checks, and browser network inspection.

## 27. Story Strategy

### Decision: B - Three bounded Stories

One large visual rewrite would make stream regressions, state-semantic changes,
and visual review difficult. More than three Stories would create artificial
fragmentation. The UI chantier should use three bounded Stories:

#### Story A - Establish workstation primitives and context strip

Scope: shared presentation primitives used by at least two workspace regions,
compact `WorkspaceContextStrip`, state/freshness vocabulary, numeric treatment,
and focused context tests.

Out of scope: changing service streams, Trade Plan/Risk contracts, or redesigning
other pages.

#### Story B - Recompose Decision Workspace desktop panels

Scope: CSS Grid shell, selector rail, quote/chart column, Trend Context summary
and evidence disclosure, Order Flow region, constraints disclosure, and
Opportunity/action column. Preserve all existing service ownership and child
component contracts.

Out of scope: embedded Plan/Risk state, mobile redesign beyond safe fallback,
new backend fields, global state, or other screens.

#### Story C - Responsive, accessibility, and stream validation

Scope: 1920/1440/1024/mobile behavior, keyboard/focus order, semantic tables,
panel-local loading/empty/error states, network duplication checks, chart resize,
and final density review.

Out of scope: new product capability, command palette, customizable layout,
and redesign of Market Detail, Positions, Dashboard, or Research.

## 28. Explicit Exclusions

- No Bloomberg branding, colors, command syntax, or copied layout.
- No draggable/resizable/user-configurable widgets.
- No third-party dashboard/grid framework.
- No NgRx or global state store.
- No new backend orchestration or context service.
- No Risk, eligibility, Opportunity, Trade Plan, or execution semantic changes.
- No embedded Plan/Risk projection without an approved authoritative contract.
- No AI badges, copilot, chat, or AI execution control.
- No new WebSocket multiplexing or global stream cache.
- No full mobile trading terminal.
- No Market Detail, Opportunities, Positions, Dashboard, or Research rewrite in
  the pilot Stories.
- No Decision History implementation; only compatibility of the visual language.
- No S1, broker, persistence, or database changes.

## 29. UI Chore Completion Criteria

The chantier is complete when:

- account and market context remain persistent and compact;
- the selected market quote, tradability, freshness, and next action are visible
  in the active-market first viewport;
- chart, Trend Context, Opportunity/action, and market microstructure are
  scannable simultaneously on desktop;
- generic card repetition and vertical sprawl are materially reduced;
- Trend Context remains deterministic and visibly distinct from Risk;
- Trade Plan preparation remains explicit and human-controlled;
- existing PlanPage remains the authoritative plan/Risk/execution surface;
- narrow layouts preserve context, actions, and state semantics;
- panel-local errors do not erase unrelated authoritative evidence;
- no duplicate market-data subscriptions or stale subscriptions remain;
- reusable primitives are stable enough for future Market Detail, Positions,
  Dashboard, and Decision History adoption;
- the visual language is dense, calm, dark slate, restrained blue, and clearly
  Trading OS rather than a generic SaaS dashboard.

## 30. Screens Before and After the Next Product Feature

### Required before Post-trade Decision History V1

- Complete Stories A, B, and C for Decision Workspace.
- Validate stream ownership and responsive behavior in the pilot.
- Stabilize panel/status/freshness/table conventions.
- Confirm the decision column does not require a second workflow engine.

### Can migrate later

1. Market Detail, because it shares chart and order-flow primitives but has a
   different exploratory/account-independent role.
2. Positions, because its operational and dangerous-action semantics should be
   migrated deliberately after the workstation conventions are proven.
3. Opportunities, because its table is already relatively dense and can adopt
   status/table primitives without blocking product work.
4. Dashboard, because it is an operational overview rather than the canonical
   decision surface.
5. Research, because it must remain semantically separate and should not delay
   the trading workstation or Decision History.

Do not require all five migrations before product development resumes.

## 31. Product-Development Gate

**Decision: YES, after completion of the three UI chantier Stories.**

Once Stories A-C are implemented and validated, the visual and component
architecture is mature enough to resume Post-trade Decision History V1. The
history feature can use the established context strip, panel, status,
freshness, metric, table, disclosure, and action-column conventions without
requiring a second broad UX investigation.

The gate does not require embedding Trade Plan or Risk into Decision Workspace.
History can be a separate account-scoped surface using the same visual language,
while the current PlanPage remains authoritative for active decision commands.

## 32. ADR Assessment

No ADR is required for the visual composition, CSS Grid layout, presentation
primitives, or input-driven child component extraction. These preserve existing
ownership and streams.

An ADR is required only if implementation introduces any of the following:

- durable global workspace context;
- a new frontend state-management architecture;
- embedded authoritative Plan/Risk state with a new contract;
- cross-feature stream caching or multiplexing;
- changed semantics for workflow stage or execution authorization.

## 33. Repository Protection

This investigation changed only:

`docs/architecture/reports/professional-decision-workspace-density-panel-architecture.md`

No production implementation was made. Story 0134 behavior remains unchanged.
No backend, Risk, execution, S1, research, audit, or unrelated files were
modified.

## 34. Exact Next Action

Create and approve Story A, **Establish workstation presentation primitives and
compress the account-scoped Workspace Context**, with the following immediate
implementation boundary:

1. preserve `WorkspaceContextView` and all route/stream semantics;
2. define the minimal panel/status/freshness/metric/disclosure conventions;
3. compress `WorkspaceContext` into the top context strip;
4. add focused Angular tests for all existing context states and accessibility
   labels;
5. validate no duplicate HTTP/WebSocket activity;
6. do not begin the desktop panel migration until Story A is reviewed and
   accepted.
