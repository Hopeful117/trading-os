# Trading OS Professional Workspace UX Investigation

## 1. Investigation Status

This is a repository investigation and UX architecture proposal. No Angular
production code, backend service, route contract, or protected audit file was
modified.

## 2. Repository State

- Branch: `main`
- HEAD: `d49ecbe5c39128d8268c7897b870f5610f705169`
- Existing unrelated untracked file: `docs/architecture/reports/v1-functional-audit.md`
- The untracked audit file is preserved and is not part of this report.

## 3. Evidence Sources

The investigation used the current Angular route table, Shell, Navbar, Sidebar,
Dashboard, Markets, Market Detail, Opportunities, Opportunity Detail, Decision
Workspace, Positions, Research, Accounts, Challenges, Economic Context,
Financial News, and Trade History surfaces. Existing Stories 0026, 0062, 0082,
0132, and 0133 were also used for product and architectural constraints.

DevLog was checked for prior workspace decisions, but the provider returned
`429 insufficient_quota`. Repository and accepted Story evidence therefore
remain authoritative for this investigation.

## 4. Executive Conclusion

Trading OS should adopt **Option A: a contextual workspace shell around the
existing account-scoped Decision Workspace**, not a standalone Bloomberg-like
terminal and not a wholesale dashboard replacement.

The first implementation should establish persistent account, market,
freshness, risk, and workflow context around the current decision lifecycle.
It should reduce context switching while preserving the existing ownership
boundaries and explicit human authorization steps.

## 5. Product Interpretation

Trading OS is a human-controlled discretionary trading platform. Its interface
must help a trader move from evidence to a validated decision and then to an
explicit execution action. The interface must not imply that Market
Intelligence, AI, or live data is itself an authorization to trade.

## 6. Current Information Architecture

The current primary domains are:

- Overview: Dashboard
- Market Intelligence: Markets, Economic Context, Financial News, Research
- Trading: Opportunities, Positions, Trade History
- Account and Risk: Accounts, Challenges

This grouping is already encoded in Story 0132 and is a sound domain grouping.
The remaining problem is not primarily the sidebar taxonomy. It is the lack of
shared context between these destinations.

## 7. Current Route Inventory

Authenticated routes include `/dashboard`, `/accounts`, `/challenges`,
`/markets`, `/markets/:marketId`, `/decision-workspace`,
`/opportunities`, `/opportunities/:opportunityId`, manual and persisted Trade
Plan routes, `/positions`, `/analytics`, `/economic-context`,
`/financial-news`, and `/research`.

The route table is flat and feature-oriented. It does not currently express a
workspace context route hierarchy or a persistent selected-account context.

## 8. Shell Assessment

The Shell owns only Navbar, Sidebar, and `router-outlet`. This is a useful
minimal boundary, but it leaves account, market, freshness, and execution
context inside individual feature pages. A future workspace context layer can
be added without moving domain ownership into the Shell.

## 9. Navigation Assessment

The Sidebar is now rendered from a typed section model and uses Angular Router
active-state facilities. It provides discoverable domain navigation and a
mobile menu fallback. This is sufficient as a primary navigation baseline.

The next navigation problem is not more links or collapsible sections. It is
making the current trading context visible and stable while the user moves
between related surfaces.

## 10. Navbar Assessment

The Navbar currently contains branding, Home, mobile menu, and Logout. It should
remain a global utility boundary rather than becoming a second feature
navigation system. Account and market context should not be duplicated as a
large navbar menu.

## 11. Dashboard Role

Dashboard is an operational overview. It correctly surfaces account equity,
daily PnL, drawdown, risk usage, open positions, Market Intelligence activity,
freshness, and explicit unavailable states. Its role is orientation and
exception awareness, not primary trade preparation.

Dashboard should link into active work rather than absorb the full decision
workflow.

## 12. Markets Role

Markets is a market catalogue and discovery surface. Market Detail provides
ticker, chart, order book, recent trades, and market constraints. It is useful
for exploration and market facts, but it is not the canonical account-scoped
decision surface.

## 13. Opportunities Role

Opportunities is the Market Intelligence triage surface. The list exposes
instrument, direction, type, timeframe, score, reference price, validity, and
rationale. It can prepare a Trade Plan directly when authoritative account
context exists, while Decision Workspace remains a secondary context-review
path.

## 14. Opportunity Detail Role

Opportunity Detail is an evidence and provenance surface. It shows the
opportunity explanation, setup conditions, timestamps, strategy match,
observations, market identity, and preparation actions. It should not become a
second risk or execution surface.

## 15. Decision Workspace Role

Decision Workspace is the strongest existing candidate for the professional
workspace. It resolves the account first, then eligible markets, then live
market context. It exposes deterministic Trend Context as analytical evidence,
manual planning, opportunity provenance, ticker, OHLC, order book, recent
trades, and market constraints.

Its weakness is density and vertical sprawl. It currently combines selection,
analysis, live market data, planning, and supporting evidence in one long page.

## 16. Positions Role

Positions is an operational monitoring surface with account selection, current
prices, PnL, protection, exposure, risk, timestamps, close confirmation, and
broker reconciliation. It has strong state semantics and explicit dangerous
actions.

Its key UX requirement is that the selected account and freshness state must be
obvious. Position monitoring must not be confused with strategy evaluation or
execution authorization.

## 17. Research Role

Research is an observability and readiness surface. Story 0133 explicitly
separates acquisition integrity, experimental readiness, and results. The page
must preserve the boundary that readiness is not performance and must not show
returns, win rate, PnL, Sharpe, or backtest results as if they were available.

Research belongs in the same product shell but not in the active trading
decision path.

## 18. Accounts and Challenges Role

Accounts establishes broker and account context. Challenges expresses a
configured trading challenge and its progress. These surfaces are configuration
and constraint surfaces, not daily market decision surfaces.

They should provide context to the workspace without forcing the trader to
revisit configuration pages during ordinary decision preparation.

## 19. Economic Context and Financial News Role

Economic Context and Financial News provide supporting market intelligence.
They should be discoverable from Market Intelligence and attachable as evidence
to a market or opportunity when the underlying contracts support that link.

They should not silently alter deterministic Risk or execution state.

## 20. Trade History Role

Trade History is an outcome and audit surface. It should support review of
executions, plans, and continuity without being positioned as a live decision
control.

## 21. Primary Trader Workflow

The primary workflow should be:

```text
Orient
  -> select or confirm account
  -> inspect opportunity or market
  -> review evidence and freshness
  -> prepare or reuse Trade Plan
  -> accept Trade Plan
  -> evaluate deterministic Risk
  -> explicitly authorize execution
  -> monitor position and reconcile outcomes
```

This preserves the existing decision pipeline and human authority boundary.

## 22. Secondary Trader Workflows

Secondary workflows are:

- Market-first exploration: Markets -> Market Detail -> Decision Workspace.
- Opportunity-first triage: Opportunities -> direct Trade Plan preparation or
  Decision Workspace context review.
- Position-first monitoring: Dashboard or Positions -> inspect risk and close or
  reconcile an exposure.
- Research review: Research -> inspect readiness and integrity without entering
  an execution workflow.

## 23. Current Context Switching Problem

Account selection is independently repeated in Dashboard, Positions, and
Decision Workspace. Market selection and market live data are independently
resolved in Markets and Decision Workspace. Opportunity detail carries account
and market parameters only for a specific transition.

This creates a risk of user uncertainty about which account, market snapshot,
or freshness state a page represents.

## 24. Proposed Context Model

The workspace should expose a small, explicit context model:

- Selected trading account
- Account mode and challenge context when applicable
- Selected market or instrument
- Market tradability state
- Data freshness and last update times
- Current workflow stage
- Risk decision state when a Trade Plan exists
- Active position context when monitoring an exposure

The model should be a projection of authoritative backend responses, not a
frontend calculation of eligibility, risk, or authorization.

## 25. Context Ownership

- Trading Core owns account ownership, Trade Plan orchestration, and execution
  state.
- Market Data owns market identity, market state, and live market facts.
- Market Intelligence owns Opportunity identity, provenance, and analytical
  evidence.
- Risk owns deterministic risk decisions.
- Broker Service owns broker-specific infrastructure and broker outcomes.
- Angular owns presentation, explicit user intent, and composition of these
  authoritative projections.

## 26. Workspace Boundary

The proposed workspace shell should coordinate context display and navigation,
not become a new domain service or frontend decision engine. It may expose a
context header, workflow rail, and page-level slots, but each feature remains
responsible for its own domain data and commands.

## 27. Layout Recommendation

Use a three-zone desktop composition:

1. Persistent primary navigation on the left.
2. Persistent compact context rail or header for account, market, freshness, and
   workflow stage.
3. Feature workspace content in the main area.

On narrow screens, collapse the context rail into an accessible context drawer
or summary block. Do not add desktop navigation collapse until runtime evidence
shows it is needed.

## 28. Density Recommendation

Trading OS should use information-dense panels and tables only where the data is
operationally actionable. The Decision Workspace should prioritize:

- account and market identity;
- current tradability and freshness;
- analytical evidence;
- Trade Plan and Risk state;
- explicit next action.

Deep provenance, raw diagnostics, and full market microstructure should remain
available through secondary panels or disclosures.

## 29. Hierarchy Recommendation

Every decision-oriented screen should make these questions answerable in the
first viewport:

- What account am I operating on?
- What market or opportunity am I inspecting?
- How current and trustworthy is the data?
- What is the current workflow state?
- What is the next safe action?

## 30. State Semantics

The interface must preserve the existing distinctions between loading, empty,
unavailable, error, stale, blocked, and unknown. Zero values must not be
rendered as unavailable, and unavailable values must not be rendered as zero.

This is especially important for account equity, risk state, market freshness,
Research integrity, and execution outcomes.

## 31. Freshness Semantics

Freshness should be shown at the point where a trader relies on the data. A
single global "live" indicator is insufficient. Market stream freshness,
analytical evidence validity, account aggregation freshness, and Research
acquisition integrity are different facts and should not be collapsed.

## 32. Risk and Human Authority

Risk must remain deterministic and fail-closed. The UI may guide the user from
Trade Plan acceptance into Risk evaluation, as Story 0082 allows, but `Execute
Trade` must remain a separate explicit authorization action.

The workspace must never make an AI recommendation look like Risk approval or
execution authorization.

## 33. AI and Analytical Evidence

AI or Market Intelligence output belongs in an evidence and recommendation
layer. It should show provenance, timestamp, validity, and authority boundary.
It must not replace deterministic market normalization, eligibility, Risk, or
execution-state transitions.

## 34. Real-Time Behavior

Live ticker, OHLC, order book, and recent trades belong to the selected market
context. They should start only after account and market eligibility are
resolved, as already enforced by Decision Workspace.

The shell should not open independent duplicate streams for every visible
feature. Stream ownership and teardown must remain local to the active market
context.

## 35. Performance Risks

The main risks are not CSS rendering. They are duplicated requests, duplicated
WebSocket subscriptions, large Market Detail payloads, and mounting every dense
panel simultaneously.

The first implementation should avoid a global store that eagerly loads all
accounts, markets, order books, news, and research data. Use lazy feature
activation and bounded projections.

## 36. Angular Architecture Direction

Use the existing standalone components, typed contracts, Observables, and async
pipe conventions. A context shell can consume route parameters and existing
service projections without introducing NgRx or another global state framework.

Any shared context service should remain a small, explicit read model with
clear ownership and invalidation rules.

## 37. Backend Contract Implications

The UX proposal does not require a new backend orchestration service. Before
implementation, verify whether existing account, market, opportunity, Trade
Plan, Risk, and freshness contracts expose sufficient authoritative identity and
timestamps.

If a missing contract is discovered, add the smallest additive contract in the
owning service rather than creating a frontend-derived semantic.

## 38. Accessibility Requirements

The workspace must preserve semantic navigation, visible keyboard focus, labelled
account and market selectors, status announcements for state changes, and
non-color-only indications for active, blocked, stale, and dangerous states.

Dense tables need responsive overflow handling and a meaningful mobile reading
order. Destructive actions need explicit confirmation and clear recovery or
reconciliation states.

## 39. Responsive Behavior

Desktop should optimize for simultaneous context and evidence. Mobile should
optimize for one active task at a time:

- account and market context remain pinned or immediately accessible;
- secondary evidence becomes disclosure content;
- tables become focused cards or horizontal scroll regions;
- execution authorization remains visually distinct.

## 40. Visual Language

Preserve the existing dark slate, restrained blue, rounded panel, and subtle
border language. Improve hierarchy through spacing, typography, state treatment,
and layout rather than introducing a new visual identity.

The target is professional and calm, not a copy of Bloomberg, a gaming-style
terminal, or an AI chat dashboard.

## 41. Navigation Decision

Keep the Story 0132 domain navigation. Do not add more top-level destinations
for every workflow step. The sidebar should answer where the user can go; the
context layer should answer what the user is currently doing.

## 42. Workspace Decision

Recommendation: **A - contextual workspace shell around Decision Workspace**.

Reject a full terminal-style replacement because it would broaden scope,
duplicate existing surfaces, and risk making deterministic boundaries harder to
read. Reject a dashboard-only approach because Dashboard is not the canonical
decision surface and cannot carry the full account-scoped market workflow.

## 43. First Implementation Story

Create one approved implementation Story titled **Establish the Account-Scoped
Trading Workspace Context**.

Its first slice should cover only:

- persistent account context projection;
- market identity and tradability context;
- freshness/status presentation;
- workflow-stage indication;
- navigation from Dashboard, Opportunities, Markets, and Positions into the
  canonical Decision Workspace without losing context;
- focused Angular tests and runtime validation.

Do not assign a new numeric Story identifier in this investigation. The project
workflow should create and approve the identifier separately.

## 44. Deferred Work

Defer the following until the first slice is validated:

- global keyboard shortcut system;
- command palette;
- multi-window or detachable panels;
- user-customizable layouts;
- chart annotation systems;
- broad cross-feature state caching;
- AI copilot or conversational execution controls;
- full mobile redesign;
- replacing every existing page with a single terminal view.

## 45. Migration Phases

1. Add a read-only context projection around the current Decision Workspace.
2. Add context-preserving entry links from Opportunities, Markets, Dashboard,
   and Positions.
3. Validate desktop, narrow viewport, state semantics, and stream lifecycle.
4. Move only duplicated decision-relevant context into shared presentation.
5. Keep feature-specific detail pages and compatibility routes during migration.

## 46. Validation Plan

Validation should include:

- Angular tests for context selection, route preservation, loading, empty,
  unavailable, stale, and error states.
- Regression tests proving no risk or execution semantics move into Angular.
- Runtime walkthrough from Dashboard, Opportunity Detail, Market Detail, and
  Positions into Decision Workspace.
- Verification that account and market streams are not duplicated or opened
  before eligibility resolution.
- Desktop and narrow viewport inspection.
- `npm run test:ci`, production build, formatting checks, and `git diff --check`
  for the eventual implementation Story.

## 47. Success Criteria

The first slice succeeds if a trader can identify the active account, market,
data freshness, and workflow stage without reselecting context or navigating
through redundant pages, while existing Risk, human validation, execution,
reconciliation, Research, and security behavior remains unchanged.

## 48. Risks and Open Questions

- Existing services may not expose one compact context projection; contract
  composition may be required.
- Persisting selected account across sessions has product and security
  implications and is not recommended for the first slice.
- Real-time stream reuse must be measured rather than assumed.
- The visual density of the current Decision Workspace needs browser validation
  before deciding which panels become secondary disclosures.
- Research, News, and Economic Context should remain adjacent in navigation but
  should not be forced into the active trading context model.

## 49. Final Recommendation

Proceed with one approved Story for an account-scoped contextual workspace
around the existing Decision Workspace. Treat the Shell as a presentation
boundary, preserve service ownership, and migrate incrementally. This is the
smallest coherent change that addresses the observed context-switching problem
without inventing a new trading architecture or weakening deterministic risk
and human execution control.
