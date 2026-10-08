# Story 0066 - Project Trend Context in the Decision Workspace

## Metadata

**ID:** `0066`

**Title:** Project Trend Context in the Decision Workspace

**Status:** CLOSED - HUMAN ACCEPTED

**Size:** MEDIUM

**Implementation Risk:** MEDIUM

## Goal

Make the persisted deterministic Trend Context understandable to the trader in
the existing account-scoped Decision Workspace before Trade Planning, without
redesigning the workspace or changing Risk/execution behavior.

## Context

The current Angular Decision Workspace already owns account selection, eligible
market selection, market state, OHLC history, live streams, freshness states,
and the manual Trade Ticket. It has no Trend Context API contract, assessment
stream, structural evidence, exclusions, invalidation, or attention outcome.

Story 0064 provides the Market Intelligence read/projection contract. The UI
must preserve ADR-048 semantics:

```text
CONTEXTUALLY_ATTRACTIVE != RISK_APPROVED != EXECUTION_AUTHORIZED
```

## Problem

The trader must currently interpret raw charts and generic opportunity evidence
manually. The first deterministic context capability is not visible before
Trade Planning, so its selectivity and explanations cannot support the daily
decision loop.

## Scope

### Included

- Add a typed Angular model for the Trend Context projection and its explicit
  loading, unavailable, stale, invalid, and degraded states.
- Add a reactive service that loads the assessment for the selected account and
  market through the existing Gateway/application boundary.
- Render a focused assessment panel in `DecisionWorkspace` for:
  - market identity and assessment timestamp;
  - direction, regime, phase, and attention outcome;
  - BIAS, SETUP, and optional TRIGGER states;
  - EMA and ATR supporting evidence;
  - alignment and contradictions;
  - hard exclusions and data-quality findings;
  - confirmed structural references and candidate invalidation;
  - freshness/provenance and a concise “why” explanation.
- Preserve visible context when the manual Trade Ticket is opened.
- Make analytical states visually distinct from Risk and execution states.
- Use existing Angular reactive patterns, Observables, async pipe, typed models,
  and workspace component conventions.
- Add focused component/service tests for selected-market changes and state
  transitions.

## Out of Scope

- New Trend Context calculations or backend persistence.
- StrategyEvaluation, StrategyMatch, opportunity, TradePlan, Risk, or execution
  changes.
- Replacing the chart, ticker, order book, recent trades, or account context.
- New scanner, ranking, news, ML, LLM, agent, or position-monitor UI.
- Automatic actions, trade buttons, order creation, or risk decisions based on
  attention outcome.
- Broad visual redesign of the Decision Workspace.

## Architectural Constraints

- ADR-048 governs authority labels and evidence provenance.
- Market Intelligence remains authoritative for assessment facts and status.
- Market Data remains authoritative for market facts and freshness.
- The frontend is presentation and human-input boundary, not analytical or Risk
  authority.
- Preserve existing manual TradePlan, Risk, and execution flows unchanged.
- Preserve existing Angular responsive/dark-slate workspace style and reactive
  conventions.
- Existing unrelated worktree changes must be preserved.

## Acceptance Criteria

- [ ] With an account and eligible market selected, the workspace loads and
      displays the corresponding Trend Context assessment.
- [ ] Loading, unavailable, stale, invalid, incomplete, degraded, and no-setup
      states are explicit and do not display fabricated direction.
- [ ] The panel displays direction, regime, phase, attention outcome, timeframe
      roles, EMA/ATR evidence, alignment, contradictions, exclusions, structural
      references, invalidation, freshness, timestamp, and why/evidence details.
- [ ] Optional TRIGGER absence is displayed as unavailable and does not appear
      as confirmation.
- [ ] `CONTEXTUALLY_ATTRACTIVE`, `WATCH`, `NO_SETUP`, and `UNKNOWN` are visibly
      distinct from Risk approval and execution authorization.
- [ ] Changing account or market clears/reloads assessment state without showing
      stale data for the previous selection.
- [ ] The existing chart, ticker, order book, recent trades, and manual ticket
      remain functional and visible according to existing workspace behavior.
- [ ] No frontend code creates StrategyMatch, TradingOpportunity, TradePlan,
      Risk approval, ExecutionIntent, or broker commands from the assessment.
- [ ] Focused Angular tests cover success, no-setup, stale/unavailable, error,
      selection changes, and authority-label semantics.
- [ ] Angular production build/check and `git diff --check` pass.

## Test Requirements

- Service contract tests for URL, account/market parameters, and typed states.
- Decision Workspace component tests for assessment rendering and selection
  changes.
- Regression tests for existing workspace, chart, market stream, manual-ticket,
  and TradePlan handoff behavior.
- Responsive/accessible rendering checks for the new panel.

## Observability and Provenance Requirements

- Display assessment timestamp, validity/freshness, profile/rule version or the
  product-safe provenance summary supplied by the backend.
- Keep evidence IDs/reference values available for inspection without exposing
  internal credentials or unrestricted payloads.
- UI errors must distinguish unavailable source data from a valid `NO_SETUP`.

## Dependencies

- Story 0064 read/projection contract.
- Existing `DecisionWorkspace`, `DecisionContextService`, `MarketService`, and
  Angular test/build conventions.
- ADR-048.

## Likely Files/Components Affected

- `trading-os-web/src/app/features/decision-workspace/`
- `trading-os-web/src/app/core/models/`
- `trading-os-web/src/app/core/services/`
- Gateway route only if the existing projection contract requires an explicit
  route addition.
- Decision Workspace and service tests.

## Risks

- A visually attractive panel could accidentally imply authorization if labels
  or proximity to the manual ticket are ambiguous.
- Reactive selection changes could display the previous market's assessment if
  cancellation and empty states are not explicit.
- Existing dirty frontend work must not be overwritten or reformatted.

## Definition of Done

- [ ] Story scope approved by the human engineer.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Projection and workspace panel implemented.
- [ ] Authority and state semantics covered by Angular tests.
- [ ] Existing workspace/manual-ticket behavior remains green.
- [ ] Angular build/check and diff validation pass.
- [ ] Human code review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
