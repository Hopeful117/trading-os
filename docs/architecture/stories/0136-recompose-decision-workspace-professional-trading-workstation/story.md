# Story 0136 - Recompose Decision Workspace into the Professional Trading Workstation

## Metadata

**ID:** `0136`

**Title:** Recompose Decision Workspace into the Professional Trading Workstation

**Status:** In Progress

---

## Goal

Recompose the existing account-scoped Decision Workspace from a sequential,
vertically stacked page into the first opinionated Trading OS workstation. The
active desktop workspace must expose account context, market quote, chart,
deterministic Trend Context summary, Opportunity/action state, and useful market
microstructure concurrently while preserving all existing authoritative data,
streams, actions, and workflow semantics.

This is a frontend composition and presentation refactor. It adds no trading
capability.

---

## Context

Story 0135 established the workstation presentation primitives and compact
`WorkspaceContextStrip`. Story 0136 is the second bounded Story in the approved
three-Story UI chantier and performs the desktop panel/grid recomposition.

The governing implementation design is:

- `docs/architecture/reports/professional-decision-workspace-density-panel-architecture.md`

Story A is merged on `main` through merge commit `29a29f4` and implementation
commit `af88e875`.

---

## Scope

- Introduce an opinionated CSS Grid workstation shell around the existing
  account-scoped Decision Workspace.
- Keep `WorkspaceContextStrip` persistent at the top and reduce duplicated page
  identity without removing the semantic H1 or necessary unresolved-state
  guidance.
- Extract input-driven presentation regions where they have coherent visual
  responsibility and focused tests:
  - compact selector/context rail;
  - market quote panel;
  - Trend Context summary with disclosed evidence;
  - Opportunity/action region;
  - Order Flow composition for Order Book and Recent Trades;
  - compact constraints/supporting-evidence region.
- Reuse Story A primitives rather than recreating local equivalents.
- Keep existing chart technology and chart component, with a bounded responsive
  workstation height and preserved ResizeObserver lifecycle.
- Preserve current account selection, market search, eligibility disclosure,
  market-first restoration, Opportunity-first context, manual preparation, and
  PlanPage navigation.
- Preserve local loading, empty, unavailable, and error semantics.
- Recompose Trend Context summary-first while retaining detailed deterministic
  evidence behind disclosure.
- Reduce Order Book and Recent Trades vertical dominance without changing their
  input contracts or stream ownership.
- Keep constraints authoritative and available as compact supporting evidence or
  disclosure.
- Provide a safe structural fallback around 1024px and avoid catastrophic mobile
  overflow; final responsive/accessibility polish remains Story 0137.
- Add focused presentation and composition tests without weakening existing
  account, market, route, Opportunity, and stream regression tests.

---

## Out of Scope

- New backend endpoints, DTOs, persistence, or service contracts.
- New HTTP requests, WebSocket subscriptions, stream multiplexing, or global
  caches.
- Moving account, Decision Context, market eligibility, market facts, Trend
  Context, Opportunity, or stream ownership into child panels.
- Embedded authoritative Trade Plan or Risk projections.
- Risk, execution, authorization, Opportunity validity, or eligibility semantic
  changes.
- New product capabilities such as Decision History, Position Intelligence,
  scanners, AI synthesis, news, cTrader, or prop-firm functionality.
- Draggable, resizable, configurable, or third-party dashboard layouts.
- NgRx, signals store, generic global state, or command palette shortcuts.
- Full mobile redesign or final accessibility audit; these belong to Story 0137.
- Redesign of Market Detail, Opportunities, Positions, Dashboard, Research, S1,
  backend, Risk, execution, or broker modules.

---

## Authority Boundaries

- `DecisionWorkspace` remains the orchestration/composition boundary.
- Angular composes authoritative projections and explicit user intent only.
- Market streams open only after account eligibility and tradable market
  resolution.
- `PlanPage` remains authoritative for Trade Plan lifecycle, Risk evaluation,
  execution readiness, and execution state.
- Trend Context remains deterministic analytical evidence and is not Risk
  approval or execution authorization.
- LONG/SHORT and Opportunity score remain explicit neutral semantics; color is
  never the only meaning.

---

## Acceptance Criteria

- [ ] Decision Workspace uses an opinionated CSS Grid workstation at 1440px-class
      desktop rather than a generic vertical card stack.
- [ ] At least the context strip, quote, chart, Opportunity/action state, Trend
      summary, and useful market microstructure are concurrently scannable in
      the active-market desktop view.
- [ ] Account and market selection are compact and no longer dominate the page;
      account-first and market-first semantics remain intact.
- [ ] The quote region exposes existing last, bid, ask, spread, tradability, and
      local freshness projections without adding a ticker subscription.
- [ ] The existing chart technology and ResizeObserver lifecycle are preserved
      with bounded workstation sizing.
- [ ] Trend Context is summary-first, preserves deterministic evidence wording,
      and moves deep evidence behind accessible disclosure without inventing
      values or Risk semantics.
- [ ] Opportunity and manual preparation actions are clearly visible in a
      stable decision/action region and continue to navigate to authoritative
      PlanPage flows.
- [ ] Order Book and Recent Trades form a coherent bounded Order Flow region and
      retain their current input contracts and semantic state behavior.
- [ ] Market constraints and supporting provenance/diagnostics are compact or
      disclosed without deleting authoritative facts.
- [ ] Local loading, empty, unavailable, and error states remain isolated and
      truthful.
- [ ] No additional account, context, market, Trend Context, Opportunity,
      ticker, OHLC, Order Book, or Recent Trades network activity is introduced.
- [ ] Existing route, eligibility, stream activation/teardown, and manual/
      Opportunity preparation regression tests remain valid.
- [ ] Focused extracted-component tests, complete Angular tests, production
      build, formatting checks, and `git diff --check` pass.
- [ ] 1440px, 1920px, and approximately 1024px layouts are structurally usable;
      narrow layouts do not catastrophically overflow.
- [ ] No backend, Risk, execution, S1, research, or unrelated files change.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md` - human authority and product identity.
- `docs/architecture/adr/ADR-014.md` - deterministic decision pipeline.
- `docs/architecture/adr/ADR-031.md` - planning context versus Risk context.
- `docs/architecture/adr/ADR-047.md` - Trade Plan origin and human authority.

ADR decision: **Not required**, provided implementation remains presentation-only
and preserves existing ownership and contracts.

---

## Relevant Stories

- `0135` - Establish workstation presentation primitives and compact context strip.
- `0134` - Establish the account-scoped trading workspace context.

---

## Relevant Modules

- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/features/markets`
- `trading-os-web/src/app/features/trade-planning`
- `trading-os-web/src/app/shared/workstation`

---

## Validation

- Focused tests for extracted selector, quote, Trend Context, action, Order Flow,
  and constraints presentation components.
- Existing Decision Workspace account, market, route, Opportunity, and stream
  regression suite.
- Complete Angular `npm run test:ci` and production build.
- Prettier/format verification and `git diff --check`.
- Browser validation at approximately 1920px, 1440px, 1024px, and narrow/mobile.
- Authenticated account-first, market-first, and Opportunity-first walkthroughs
  where runtime data permits.
- Network inspection for HTTP/WebSocket multiplication and stream teardown.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Human approval received for this Story and plan.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Documentation reconciliation completed.
- [ ] Independent Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
