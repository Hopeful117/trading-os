# Story 0134 - Implementation Report

## 1. Executive Decision

**B - PARTIAL_IMPLEMENTATION.** The authoritative account, market, tradability,
freshness, route context, and Opportunity provenance subset is implemented.
Account mode/challenge context, a universal workflow lifecycle, and
Position-to-Decision-Workspace navigation remain deferred.

## 2. Starting Branch

`main`

## 3. Starting HEAD

`d49ecbe5c39128d8268c7897b870f5610f705169`

## 4. Starting Worktree

The worktree already contained untracked reports:

- `docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`
- `docs/architecture/reports/v1-functional-audit.md`

Both were preserved.

## 5. Story

`0134 - Establish the Account-Scoped Trading Workspace Context`

## 6. Governing Investigation

`docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`

## 7. Existing Decision Workspace

Decision Workspace remains canonical. It resolves account context, eligible
markets, Market Data state, Trend Context, streams, constraints, and manual or
Opportunity Trade Plan preparation.

## 8. Existing Entry Workflows

Market-first, Opportunity-first, Account-first, and Position-first workflows
were preserved. Only Market Detail and existing Opportunity Detail transitions
were changed or covered.

## 9. Account-First Assessment

Preserved. Account selection still precedes Decision Context resolution and live
market stream activation.

## 10. Market-First Assessment

Improved. Market Detail now links to Decision Workspace with `marketId` without
requiring an account. Selecting an account later preserves the pending market.

## 11. Opportunity-First Assessment

Preserved and strengthened. Opportunity and market query context remains when an
account is selected from an account-less Opportunity entry.

## 12. Position-First Assessment

Deferred. The current visible position contract does not provide a canonical
normalized market ID suitable for a safe Decision Workspace transition.

## 13. Context Model Implemented

Added `WorkspaceContextView` as a presentation-only model containing account,
market, Opportunity, tradability state, market freshness, account/market load
state, workflow label, and next safe action.

## 14. Account Context Semantics

The context surface displays the authoritative Decision Context account name and
base currency. Loading, unavailable, error, and selected states remain distinct.

## 15. Market Context Semantics

The surface displays normalized market symbol, provider, product type, and
Market Data state when loaded.

## 16. Tradability Semantics

Tradability is rendered from `MarketResponse.marketState.tradable` and
`tradingStatus`. Angular does not calculate eligibility.

## 17. Freshness Semantics

The surface reuses the existing Decision Workspace ticker timestamp classification:
`LIVE`, `RECENT`, `STALE`, or `UNAVAILABLE`. No generic inferred `LIVE` flag was
introduced.

## 18. Workflow-Stage Semantics

The limited presentation labels are `Select account`, `Resolve account context`,
`Select market`, `Review opportunity`, and `Prepare Trade Plan`. They are derived
from current route/context state and are not persisted or used for authorization.

## 19. Context Source of Truth

Angular composes existing authoritative projections. It does not become the
source of truth for account eligibility, market eligibility, Risk, Trade Plan
validity, or execution authorization.

## 20. Route and Context Propagation

Existing query parameters remain the transport: `accountId`, `marketId`, and
`opportunityId`. No route hierarchy redesign was introduced.

## 21. URL Addressability

Market and Opportunity context remain URL-addressable. Market-first navigation
adds only `marketId`; it does not expose or invent an account ID.

## 22. Frontend State Architecture

No NgRx, generic global store, eager cache, or backend-driven workspace state was
introduced. The context projection is local to Decision Workspace and reactive
through existing Observables and `async` pipe usage.

## 23. Backend Contract Assessment

Existing contracts were sufficient for the implemented subset. No backend field
or endpoint was required.

## 24. Backend Changes

None.

## 25. Risk Boundary

No Risk calculation or Risk semantic was added. Opportunity score, Trend Context,
and freshness are not rendered as Risk approval.

## 26. Human Authorization Boundary

No execution action or Execution Intent path changed. Explicit execution
authorization remains separate and human-controlled.

## 27. AI Boundary

Opportunity provenance remains a distinct context item. The new surface does not
present analytical evidence as deterministic authorization.

## 28. Stream Lifecycle

The context component is presentational and injects no Market Data service. It
consumes the existing parent projections. Existing stream teardown remains in
Decision Workspace.

## 29. Duplicate Request/Subscription Findings

No new HTTP or WebSocket subscription path was introduced. The new combined
projection uses existing `shareReplay` streams. Opportunity lookup is keyed by
`opportunityId`, so account selection does not duplicate an in-flight request.
Responses are checked against the route market context before projection.
Runtime network inspection was not available in this validation run, so
browser-level duplication evidence remains pending.

## 30. Sidebar Impact

None. Story 0132 navigation remains unchanged.

## 31. Navbar Impact

None. Navbar remains a global utility boundary.

## 32. Dashboard Impact

None. Dashboard remains an operational awareness surface; no speculative link
was added.

## 33. Markets Impact

Market Detail now exposes a secondary `Review in Decision Workspace` link while
remaining usable without account context.

## 34. Opportunities Impact

Existing Opportunity Detail navigation remains intact. Regression coverage was
preserved through the existing test suite.

## 35. Positions Impact

No code change. Direct navigation was deferred because the current contract does
not safely expose normalized market identity for this purpose.

## 36. Research Impact

None. Research integrity, readiness, and results remain separate.

## 37. Shared UI Primitives

Added one local standalone `WorkspaceContext` component with its own template,
styles, and focused tests. No design system was introduced.

## 38. Accessibility Validation

The component uses semantic section/article structure, labelled context content,
text-based state indicators, and no color-only state dependency. Keyboard focus
was not browser-validated in this run.

## 39. Responsive Validation

Responsive CSS provides four-column desktop, two-column medium, and stacked
narrow layouts. Browser viewport inspection was not available in this run.

## 40. Visual/Density Validation

The context surface is compact and placed before dense Decision Workspace
content. Browser visual validation remains pending; no broad workspace CSS
redesign was performed.

## 41. Market-First Runtime Walkthrough

Code-level and Angular regression validation confirms Market Detail generates a
Decision Workspace link with `marketId` and no `accountId`. Full authenticated
runtime walkthrough was not available.

## 42. Opportunity-First Runtime Walkthrough

Existing route behavior and new regression coverage preserve Opportunity and
market query context when account selection occurs from an account-less entry.
Full authenticated runtime walkthrough was not available.

## 43. Account-First Runtime Walkthrough

Existing Decision Workspace tests confirm account selection resolves context and
eligible markets before market loading. The compact context projection is
covered by focused tests.

## 44. Position-First Decision

No transition implemented. This is an intentional semantic deferral, not a
missing accidental link.

## 45. Tests

Focused validation passed: 2 affected test files, 50 tests.

Complete Angular validation passed: 53 test files, 485 tests.

## 46. Angular Build

`npm run build` passed. Existing bundle and component-style budget warnings remain
visible; no build error remains.

## 47. Backend Validation

Not applicable. No backend code or contract changed.

## 48. Runtime Validation

Completed against the authenticated local stack. Docker services were running
and the Angular application was served on `http://127.0.0.1:4200`.

- Market-first navigation opened Market Detail and exposed `Review in Decision
  Workspace` with `marketId` and no invented `accountId`.
- Selecting the disposable `PAPER` account `Runtime Paper Validation` preserved
  the pending market context and resolved the authoritative account projection.
- Account-first navigation resolved `174` eligible markets; selecting `0G/EUR`
  produced the account-scoped Decision Workspace with `Tradable / OPEN` and the
  `Prepare Trade Plan` presentation stage.
- The live Opportunity scan completed through the UI with `1` eligible, `1`
  completed, `0` failed, and `1` opportunity found.
- Opportunity-first navigation opened the generated BTC/USD trade-plan route
  with the authoritative BTC/USD market context preserved.
- Desktop inspection at `1440x900` showed the compact four-column context
  surface without excessive growth in the context section.
- Narrow inspection at `390px` showed the stacked context surface within the
  viewport; keyboard focus advanced to a semantic link/control.
- Network inspection showed one account catalogue request and one Decision
  Context request for the active workspace. The four market subscriptions were
  distinct (`TICKER`, `OHLC`, `ORDER_BOOK`, `TRADES`); no duplicate identical
  subscription was observed.
- Position-first navigation remains intentionally deferred because the current
  visible position contract does not expose a canonical normalized market ID.

## 49. Performance Validation

No new eager global loading or direct stream subscription was introduced. Runtime
network inspection confirmed the workspace reused its existing projections and
did not create duplicate identical subscriptions.

## 50. ADR Decision

No ADR required. The implementation is a local typed presentation projection
around the existing Decision Workspace and does not introduce durable global
context ownership or a new routing/state architecture.

## 51. Documentation

Created and maintained the Story, Repository Analysis, Implementation Plan, and
this Implementation Report. The UX Investigation was not modified.

## 52. Diff Review

The complete intended code and documentation file set was inspected. `git diff
--check` passed and affected frontend files pass Prettier verification.

## 53. Repository Protection

`docs/architecture/reports/v1-functional-audit.md` remains unmodified and
untracked. No secrets, generated artifacts, backend files, Research files, or
Risk/execution semantics were changed.

## 54. Commit Hash

None. Commit, push, and merge were not authorized.

## 55. Deferred Work

- Account mode/challenge context until an authoritative projection exists.
- A universal workflow lifecycle state.
- Position-first Decision Workspace navigation.
- Position-first Decision Workspace navigation.
- Broader Decision Workspace density/layout redesign.

## 56. Recommended Next Story

Add the smallest runtime-validation follow-up for the contextual workspace,
covering authenticated browser walkthroughs, network/subscription inspection,
and visual/accessibility confirmation before expanding context dimensions.

## 57. Exact Next Action

Review the implementation diff and create a focused human commit only after
validation.

## Review Follow-up

The independent review findings were corrected:

- account catalogue loading no longer masks an already resolved Decision Context;
- account selection no longer duplicates or invalidates the same Opportunity
  request;
- an Opportunity is not projected when its market differs from the route market.

## Documentation Reconciliation

Documentation update: required and completed. The Story status and implementation
record now reflect the partial implementation, deferred dimensions, validation
limitations, and absence of backend changes.

## Vault Outcome

The vault was not consulted for implementation because repository Story, ADR,
and source evidence were sufficient. No vault action or proposal artifact is
required.
