# Repository Analysis - Story 0157

## Baseline

* `main` and `origin/main` are both at `e32973abc6721775eabf07b841d452d659c15c61`.
* The worktree was clean before Story 0157 was created.
* DevLog applicability was checked twice but the Story Agent was unavailable:
  the first request was rate-limited and the second failed with an upstream
  provider error. No DevLog context is therefore used as an authority.

## Current Implementation

### Decision Workspace

`trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
already owns the account-first workflow. It resolves account-scoped market
eligibility, restores route context, loads the selected market and opportunity,
opens market streams, and navigates to manual or Opportunity-derived Trade Plan
flows. Its existing `workspaceContext$` derives account, market, opportunity,
freshness, workflow stage, and next action for the context strip.

`decision-workspace.html` currently presents:

* account selection;
* account-scoped market discovery;
* selected-market quote and analytical context;
* Order Book and Recent Trades;
* manual and Opportunity Trade Plan actions;
* explicit loading, unavailable, stale, and error states.

`decision-workspace.spec.ts` covers account-first loading, account changes,
market eligibility, context loading, disclosure behavior, navigation, and stream
lifecycle behavior. The workspace has an existing test seam for additional
account-scoped context assertions.

### Dashboard Contract

`trading-os-web/src/app/core/models/dashboard-summary.model.ts` already models
the required bounded facts:

* account balance, equity, daily PnL, and drawdown;
* Risk status, used/remaining risk, loss and drawdown limits, and rules;
* open positions with valuation, protection, exposure, and freshness facts;
* alerts with severity and source identifiers;
* aggregate Dashboard freshness and warnings.

`DashboardService.findDashboard(accountId)` calls the existing gateway route
`v1/accounts/{accountId}/dashboard` and returns the provider-neutral
`DashboardSummary` contract.

The existing `Dashboard` page implements a polling `switchMap` over the selected
account and already renders truthful loading and error states. Its aggregation
must not be duplicated in the Cockpit.

## Ownership Findings

* ADR-019 makes Trading Core the owner of the Dashboard contract and explicitly
  prohibits frontend inter-service aggregation or frontend financial
  calculation.
* ADR-014 requires the decision pipeline to terminate in human validation.
  Cockpit display state must not authorize, reject, or execute a trade.
* ADR-031 separates planning context from financial authorization. The Cockpit
  may display authoritative Risk facts but must not turn them into planning or
  execution decisions.
* `DecisionWorkspace` is the appropriate frontend composition boundary because
  it already owns the account-scoped route and market workflow.

## Smallest Safe Implementation Boundary

The first implementation can remain within `trading-os-web`:

* reuse `DashboardService` and `DashboardSummary`;
* add an account-scoped observable to `DecisionWorkspace` or a small local
  presentation component;
* compose account/Risk/position/alert/freshness presentation into the existing
  workspace shell;
* use links to existing specialized pages rather than adding new workflow
  ownership;
* add focused frontend tests and preserve existing workspace tests.

No backend contract, DTO, gateway route, persistence, global store, or ADR is
required by the current scope.

## Risks and Mitigations

* **Stale account facts shown after account change:** use `switchMap` keyed by
  the selected account and clear the previous summary on transition.
* **Dashboard aggregation duplicated in Angular:** render existing fields only;
  do not calculate PnL, exposure, or Risk values in the frontend.
* **Cockpit implies authorization:** label Risk and market facts as context and
  preserve existing human-controlled Trade Plan and execution actions.
* **Existing workspace regressions:** keep the current workspace coordinator,
  route restoration, stream teardown, and child contracts unchanged.
* **Responsive density regression:** reuse workstation primitives and validate
  desktop plus narrow mobile layouts.

## Implementation Plan Decision

An independent implementation-plan artifact is not required at this stage. The
approved Story, this analysis, existing ADRs, and the current Angular patterns
provide sufficient implementation guidance for a bounded frontend change.

Implementation remains gated on human approval of this Repository Analysis.
