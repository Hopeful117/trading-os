# Story 0060 - Market-Integrated MANUAL Trade Ticket

## Metadata

**ID:** `0060`
**Title:** Market-Integrated MANUAL Trade Ticket
**Status:** RUNTIME_VALIDATED - HUMAN REVIEW PENDING

---

## Goal

Allow an authenticated human trader to prepare a MANUAL TradePlan directly from
the account-scoped `DecisionWorkspace` while retaining the selected market and
its live market context.

The target flow is:

```text
DecisionWorkspace
    -> selected account
    -> selected eligible market
    -> live market context
    -> MANUAL Trade Ticket
    -> MANUAL TradePlan (PROPOSED)
    -> existing TradePlan lifecycle
```

---

## User Value

The trader can observe the market and prepare a discretionary trade without
leaving the decision surface or reselecting the account and market already
known by Trading OS.

---

## Context

Story `0057` introduced the account-scoped Market Decision Workspace. It owns
account context, eligible-market selection, ticker, OHLC, order book, recent
trades, market constraints and freshness state.

Story `0058` introduced the `MANUAL` TradePlan origin and the backend creation
contract. The backend remains reusable and manual plans continue through the
same proposal, Risk and execution lifecycle as opportunity-origin plans.

The investigation
`docs/investigations/market-decision-workspace-ux-2026-09-21.md` established
that the standalone `ManualTradePage` unnecessarily removes the user from the
live market context. Its useful form logic must be extracted into a reusable
ticket and composed by `DecisionWorkspace`.

The existing query-param bridge:

```text
/trade-planning/manual?accountId=<id>&marketId=<id>
```

may remain as a fallback, but it is not the primary interaction delivered by
this Story.

---

## Problem

The current MANUAL form is a standalone page. It independently loads and asks
the user to select accounts and markets even when `DecisionWorkspace` already
has authoritative account-scoped selections and live market data.

This creates unnecessary context loss and makes the user leave the decision
surface before entering the trade proposal.

---

## Scope

### Included

- Extract the useful MANUAL form and submission logic into a reusable Angular
  `ManualTradeTicket` component following repository conventions.
- Embed the ticket directly in `DecisionWorkspace`.
- Inherit the selected account from the workspace.
- Inherit the selected eligible market from the workspace.
- Do not show redundant account or market selectors in the contextual ticket.
- Display the selected account, market identity, current/reference price when
  available, and tradability state as contextual or read-only information.
- Keep the surrounding ticker, chart, order book, recent trades and constraints
  visible while preparing the ticket.
- Reuse the existing MANUAL TradePlan API and backend orchestration.
- Preserve `MANUAL` provenance.
- Navigate successful creation to the existing `PlanPage` with a `PROPOSED`
  TradePlan.
- Keep the standalone MANUAL route as a fallback during migration.
- Reuse the extracted ticket in the standalone route where practical so that
  form and submission logic are not duplicated.
- Add focused Angular component and integration tests.
- Add backend regression tests only if the existing MANUAL contract is affected.
- Perform PAPER runtime validation without LIVE execution.

### Field classification

The ticket must classify fields as follows:

- `CONTEXT`: account, market, market state and available live price.
- `USER INPUT`: direction, entry type, limit price when applicable, reference
  price when not available from authoritative market data, quantity, monetary
  risk, stop loss, stop rationale, take profit, thesis, confirmation
  conditions, invalidation conditions and management rules.
- `DERIVED`: effective entry price and request normalization.
- `READ-ONLY`: selected account, selected market and market tradability state.

The frontend must not expose `planningContextId` or `contextVersion`.

---

## Non-Goals

- Opportunity journey redesign.
- `OpportunityResponse.marketId` changes.
- Opportunity-to-workspace navigation.
- Advanced position management.
- Pending-order management.
- Post-execution stop-loss or take-profit management.
- New Risk architecture or risk rules.
- New Execution architecture.
- Direct broker calls from the frontend.
- Direct execution or risk bypass.
- LIVE runtime execution.
- Removal of the standalone MANUAL route.
- Duplication of chart, order book or market streaming infrastructure inside
  the ticket.

---

## Architecture Invariants

The existing lifecycle remains authoritative:

```text
MANUAL TradePlan
    -> PROPOSED
    -> human acceptance
    -> ACCEPTED
    -> deterministic Risk Evaluation
    -> explicit human execution authorization
    -> ExecutionIntent
    -> T1
    -> Execution
    -> Position
```

This Story must not introduce:

- `DirectExecution`;
- `ManualExecutionEngine`;
- frontend broker execution;
- a second TradePlan pipeline;
- a Risk bypass;
- frontend ownership of PlanningContext;
- a second market-streaming implementation.

ADR-047 remains authoritative: a manual proposal is a `MANUAL` TradePlan, not
an execution command and not a synthetic Opportunity.

---

## Acceptance Criteria

- [x] A trader with a resolved account context can open the MANUAL ticket from
      `DecisionWorkspace` without leaving the workspace.
- [x] The ticket is not shown before an account and eligible market are
      selected, or it presents an explicit state explaining what is missing.
- [x] The contextual ticket receives the selected account from the workspace
      and does not require a second account selection.
- [x] The contextual ticket receives the selected eligible market from the
      workspace and does not require a second market selection.
- [x] The selected account and market are displayed as read-only context.
- [x] The selected market tradability state is visible before submission.
- [x] The surrounding ticker, chart, order book and recent-trades sections
      remain visible while the ticket is open.
- [x] The ticket does not create its own market streams or duplicate market
      data infrastructure.
- [x] The ticket submits through the existing MANUAL TradePlan capability.
- [x] Successful submission creates a TradePlan with origin `MANUAL` and state
      `PROPOSED`.
- [x] Successful submission does not create an ExecutionIntent, call a broker,
      or execute an order.
- [x] Successful submission navigates to the existing `PlanPage`.
- [x] The standalone MANUAL route remains available as a fallback.
- [x] The standalone route and contextual route do not maintain independent
      copies of the MANUAL form and submission business logic.
- [x] Planning context identifiers remain internal and are not exposed in the
      ticket or request payload.
- [x] Account ownership and market validation remain authoritative in the
      backend.
- [x] `MANUAL` provenance is preserved and is not changed by the workspace
      context.
- [x] Focused Angular tests cover context inheritance, missing context,
      submission and handoff.
- [x] Existing MANUAL backend and frontend regression tests remain green.
- [x] PAPER runtime validation demonstrates TradePlan creation without LIVE
      execution or broker mutation.
- [x] `git diff --check` passes.

---

## Constraints

- ADR-047 is authoritative for manual TradePlan provenance and lifecycle.
- ADR-001 and ADR-014 remain authoritative for human authority and the decision
  pipeline.
- ADR-028 remains authoritative for deterministic Risk decisions.
- ADR-029 remains authoritative for ExecutionIntent and execution boundaries.
- ADR-031 remains authoritative for planning-context responsibilities.
- ADR-044 remains authoritative for authenticated actor and ownership
  propagation.
- `DecisionWorkspace` remains the authority for selected account-scoped market
  context in the frontend.
- Market Data remains authoritative for market facts, timestamps and
  tradability.
- Trading Core and Market Intelligence remain authoritative for account,
  planning and TradePlan decisions.
- Angular implementation should preserve Observable, typed state and async
  pipe conventions.
- Existing unrelated worktree changes must not be discarded or reformatted.

---

## Architectural Decision

`NEW_ADR_REQUIRED = NO`.

This Story composes the existing `DecisionWorkspace`, MANUAL TradePlan contract
and TradePlan lifecycle. A new ADR is required only if implementation discovers
that the ticket requires a new domain owner, a second execution path, a new
Risk authority or a change to accepted responsibility boundaries.

---

## Relevant ADRs

- `ADR-001` - Trading OS Vision and Human Authority.
- `ADR-006` - Market Data and Market State Responsibilities.
- `ADR-014` - Trading Decision Pipeline.
- `ADR-028` - Deterministic Risk Domain.
- `ADR-029` - Execution Domain Architecture.
- `ADR-031` - Trade Planning Context and Risk Context Responsibilities.
- `ADR-044` - Inter-Service Trust and Actor Propagation.
- `ADR-047` - Manual Trade Plans as a First-Class Trade Plan Origin.

---

## Related Stories

- `0056` - Account-First Market Decision Context.
- `0057` - Account-Scoped Market Workspace.
- `0058` - Create Manual-Origin Trade Plans.
- `0059` - Evaluate Manual Trade Plans through Deterministic Risk.

---

## DevLog Context

DevLog project `trading-os` was resolved and searched before implementation.

Relevant historical evidence:

- Commit `af5181cae0debce5abef2141ad24ab3d85303fc7` implemented Story 0057 and
  established the account-scoped workspace, selected market URL state and live
  market stream ownership in the frontend.
- Commit `a7e87b0b1d57e426deda461a38c6f429a41ad96b` merged Story 0057 and
  preserved its story, implementation plan, repository analysis and tests as
  the canonical workspace history.
- Commit `e71b239df6ff933078c48d05376258d6180cfac9` implemented Story 0058 and
  established the MANUAL TradePlan origin, backend creation contract and ADR
  047 boundary.
- The exact future Story name was not present in imported DevLog history. The
  current repository Story sequence and the approved prompt establish `0060`
  as the next Story number.

DevLog evidence is historical context. The current repository remains
authoritative for the physical implementation state.

---

## Relevant Investigation

- `docs/investigations/market-decision-workspace-ux-2026-09-21.md`

The investigation established that `DecisionWorkspace` already owns the useful
account-scoped market context and that the standalone MANUAL form should become
a reusable ticket. Opportunity journey changes are explicitly deferred.

---

## Relevant Modules

- `trading-os-web`.
- `trading-core` only if an existing MANUAL contract regression is discovered.
- `market-intelligence` only if an existing MANUAL contract regression is
  discovered.

---

## Validation

- Focused Angular tests for `ManualTradeTicket`.
- `DecisionWorkspace` integration tests for context visibility and submission
  handoff.
- Existing MANUAL page tests.
- Existing Trading Core and Market Intelligence MANUAL regression tests.
- Angular production build.
- PAPER runtime validation through the official UI.
- `git diff --check`.

Runtime validation must stop before LIVE execution. Creation of a `PROPOSED`
MANUAL TradePlan is sufficient for this Story if the environment cannot safely
continue through PAPER Risk and execution.

## Implementation Evidence

- Branch: `feature/market-manual-trade-ticket`.
- Frontend tests: `45` test files and `336` tests passed with
  `npm run test:ci`.
- Angular production build passed with `npm run build`.
- Prettier check passed for all changed frontend files.
- `git diff --check` passed.
- Runtime PAPER validation is blocked before market selection because the
  running Gateway initially returned `404 Not Found` for
  `/api/v1/intelligence/decision-context/{accountId}`. This was resolved by
  rebuilding the current `market-intelligence` and `gateway` images and
  recreating their containers from the existing worktree.
- The following `502 Bad Gateway` was traced to the `trading-web` Nginx
  container retaining the old Gateway container IP after Gateway recreation.
  Its error log showed `connect() failed ... upstream 172.19.0.10:8080`.
  Restarting only `trading-web` refreshed Docker DNS resolution; no Nginx or
  application configuration change was required.
- After recovery, the official UI loaded the account list, resolved
  `1351` eligible markets for `Demo PAPER 0051`, and opened `AIXBT/EUR` with
  market state `OPEN` and reference price approximately `0.01824`.
- The integrated ticket remained on `/decision-workspace` and displayed the
  inherited account and market without account or market selectors. Ticker,
  chart, order book and recent-trades sections remained visible around it.
  The ticker was `STALE`; order book and recent trades reported their existing
  unavailable/waiting states rather than fabricating values.
- MANUAL TradePlan created through the integrated ticket:

  ```text
  TradePlan: 3da091b1-a2ce-42df-872b-5dcd04c6d55f
  Version: 1
  Origin: MANUAL
  State: PROPOSED
  Account: 22a19424-7572-4802-b89e-e65e591c2f5c
  Market: AIXBT/EUR
  ```

- The plan was accepted through the official UI and Risk Evaluation was
  requested. Risk rejected it deterministically:

  ```text
  Evaluation: e90b568a-6b4b-414b-b366-066b3f42a764
  Approved: No
  Reason: CURRENT_MARKET_VALUATION_UNAVAILABLE
  ```

- Execution was intentionally not attempted after the Risk rejection. No
  ExecutionIntent, T1 attempt, broker mutation or Position was claimed.
- Tester validation result: `PASS WITH WARNINGS`. The warning is the existing
  runtime market-data degradation (`STALE`/`UNAVAILABLE` states), not a loss of
  workspace or ticket context.

---

## Definition of Done

- [x] Repository status and current branch inspected.
- [x] Dedicated implementation branch created.
- [x] Investigation artifact reviewed.
- [x] Relevant Story and ADR artifacts reviewed.
- [x] DevLog history checked for Stories 0057 and 0058.
- [x] Story scope recorded in the canonical Story artifact.
- [x] Reusable `ManualTradeTicket` implemented.
- [x] Ticket integrated into `DecisionWorkspace`.
- [x] Standalone MANUAL route preserved and reused where practical.
- [x] Acceptance criteria validated with evidence; the Risk rejection and
      market-data freshness limitation are recorded above.
- [ ] Human code review completed.
- [ ] Human commit created.
