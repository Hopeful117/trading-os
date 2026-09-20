# Story 0056 - Account-First Market Decision Context

## Metadata

**ID:** `0056`
**Title:** Account-First Market Decision Context
**Status:** IMPLEMENTATION_COMPLETE - HUMAN REVIEW PENDING

---

## Goal

An authenticated trader selects a trading account before exploring markets in
the future Market Decision Workspace. Trading OS then resolves the account
context and exposes the markets that are actually eligible for that account.

The selected account is the first decision filter. Market exploration, live
market context, opportunities, Trade Plans, and Risk decisions must remain
scoped to that account context.

```text
authenticated trader
    -> owned account
    -> effective account context
    -> account-eligible markets
    -> selected market
    -> decision workspace
```

---

## Context

The repository already provides:

* authenticated account listing and account ownership checks in Trading Core;
* account fields for risk-profile and Trade Planning Profile references;
* a global Market Data catalogue and market tradability state;
* account-aware Active Scan scope resolution in Market Intelligence;
* Angular account selection patterns used by the scan and Trade Plan flows.

The existing Market Data catalogue is global. A market being globally tradable
does not by itself prove that it is available for the selected account. The
future Decision Workspace must not expose a global catalogue as if it were an
account-specific decision universe.

---

## Problem

The proposed Market Decision Workspace cannot safely start from market
selection. Account type, broker context, effective risk profile, planning
profile, currency, and account usability may change the markets and rules that
apply to a decision.

Without an account-first context:

* the frontend could show markets that cannot be used by the account;
* the user could inspect opportunities outside the intended trading context;
* Trade Plan preparation could start with an invalid account/market pair;
* the UI could accidentally duplicate account or Risk eligibility rules.

---

## Scope

### Included

* A dedicated account-first context contract for the Decision Workspace, or a
  narrowly scoped extension of the existing authoritative account-aware scope
  resolution contract if that is proven sufficient.
* Authenticated retrieval of accounts owned by the current user.
* Validation of the selected account using authoritative Trading Core data.
* Exposure of the effective account context already represented by the current
  model, including account mode/type where available, broker association,
  base currency, effective Risk Profile reference, and effective Trade
  Planning Profile reference.
* Resolution of candidate markets from the Trading OS market catalogue.
* Deterministic account-aware market eligibility resolution.
* Explicit exclusion diagnostics for markets that are unavailable because of
  missing market data, non-tradability, account/broker incompatibility, or
  another established deterministic reason.
* Angular models and reactive services for account-first loading.
* A minimal account-first Workspace entry state that prevents market
  selection until the account context is resolved.
* Resetting dependent market state when the selected account changes.
* Backend, frontend, ownership, cross-account, and cross-market tests.

### Contract intent

The returned context should be sufficient for the frontend to distinguish:

* the selected account;
* the effective account context;
* eligible markets;
* excluded markets and deterministic reasons;
* the resolution timestamp or freshness information.

The contract must not require Angular to interpret raw Risk rules or broker
rules.

---

## Out of Scope

* Full Market Decision Workspace layout and responsive design.
* Ticker, OHLC, order-book, and recent-trades presentation.
* Opportunity and Active Scan presentation.
* Trade Plan creation, accept/reject, or re-planning.
* Risk evaluation or changes to Risk Domain rules.
* Broker order execution.
* Watchlists or persisted workspace preferences.
* News, economic calendar, AI Engine, or passive scanning.
* Global market ranking or recommendation claims.
* Automatic assignment or mutation of Risk Profiles or Trade Planning
  Profiles.
* Replacing the existing Active Scan scope-resolution responsibility without
  an explicit architectural decision.

---

## Acceptance Criteria

* [x] An authenticated trader sees only accounts returned for that trader by
      the authoritative account API.
* [x] No market is selectable before an account is selected and its context is
      successfully resolved.
* [x] An account belonging to another user cannot be used to resolve a
      decision context.
* [x] The resolved context preserves the canonical account identity and the
      effective Risk Profile and Trade Planning Profile references when
      present.
* [x] Market candidates come from the authoritative Trading OS market
      catalogue.
* [x] Market eligibility is resolved by backend business logic; Angular does
      not reimplement account, broker, currency, or Risk eligibility rules.
* [x] A market that is not currently tradable is excluded with a deterministic
      reason.
* [ ] A market incompatible with the selected account context is excluded with
      a deterministic reason when the repository has authoritative facts to
      make that determination.
* [x] Eligible markets and excluded markets are distinguishable in the
      response and UI.
* [x] Changing the selected account clears the selected market and all
      account-dependent loading state.
* [x] No market-data live subscription is opened before a market is selected
      in a successfully resolved account context.
* [x] Account loading failure, account-not-found, context-resolution failure,
      and no-eligible-market states are rendered distinctly and without
      fabricated data.
* [ ] Cross-account tests prove that account ownership and eligibility are not
      derived from caller-provided frontend state.
* [x] Existing Active Scan behavior remains unchanged unless the implementation
      explicitly reuses a compatible shared application service without
      changing its contract.
* [x] Affected backend tests, Angular tests, production build, and
      `git diff --check` pass.

---

## Constraints

* Trading Core remains authoritative for account identity, ownership, and
  account-specific trading context.
* Market Intelligence remains authoritative for Active Scan scope resolution
  and intelligence artifacts.
* Market Data remains authoritative for the global market catalogue,
  tradability, and market facts.
* Risk Domain remains authoritative for final risk evaluation.
* The frontend must not calculate eligibility, sizing, risk, or broker
  compatibility from duplicated rules.
* Preserve JWT, Gateway, and actor propagation conventions.
* Preserve Angular Observable and async-pipe conventions.
* Do not expose broker credentials or provider-specific payloads.
* Do not introduce autonomous trading behavior.

---

## Architectural Decision

`NEW_ADR_REQUIRED = NO` for the initial implementation if the contract reuses
existing Trading Core account authority and the existing Market Intelligence
account-aware eligibility boundary.

A new ADR is required before implementation if the work introduces a new
service as the authority for account-market eligibility, merges Risk and Trade
Planning Profile responsibilities, or changes the responsibility boundary of
Active Scan scope resolution.

---

## Relevant ADRs

* `ADR-001` - Trading OS Vision and Human Authority
* `ADR-006` - Market Data and Market State Responsibilities
* `ADR-014` - Trading Decision Pipeline
* `ADR-020` - Market Intelligence Architecture
* `ADR-028` - Deterministic Risk
* `ADR-031` - Trade Planning and Risk Context Responsibilities
* `ADR-033` - Active and Passive Market Intelligence Orchestration
* `ADR-043` - Account and BrokerAccount Identity
* `ADR-044` - Inter-Service Trust and Actor Propagation
* `ADR-046` - Effective PAPER Trade Planning Profile

---

## Related Stories

* `0005` - Account-Aware Active Scan Scope Resolution
* `0022` - Trigger a Market Scan from the Product
* `0023` - Decide a Proposed Trade Plan from an Opportunity
* `0035` - Scope Active Market Scans and Surface Per-Market Results
* `0043` - Explicit PAPER Risk-Profile Onboarding
* `0053` - Make a Trade Planning Profile Effective for PAPER Accounts
* `0055` - Make PAPER Trade Plan Sizing Compatible with Effective Risk

---

## Relevant Modules

* `trading-core`
* `market-data`
* `market-intelligence`
* `gateway`
* `trading-os-web`

---

## Validation Expectations

* Trading Core account ownership and context tests.
* Market Intelligence eligibility and contract tests.
* Market Data catalogue/tradability regression tests where affected.
* Gateway route and authentication validation where affected.
* Angular account-first state, reset, empty, error, and cross-context tests.
* Angular production build.
* `git diff --check`.
* Authenticated runtime walkthrough with at least two account contexts when
  available.

---

## Definition of Done

* [x] Repository Analysis approved.
* [x] Implementation Plan approved.
* [x] Story scope and contract approved.
* [x] Implementation completed within this Story's scope.
* [x] Acceptance criteria validated with evidence.
* [ ] Human code review completed.
* [ ] Human commit created.

---

## Implementation Notes

The current repository provides authoritative account ownership and market
tradability facts, but it does not yet expose broker/instrument capability
facts sufficient to distinguish every account-specific market restriction.
The implementation therefore preserves the account-first contract and the
existing deterministic scope boundary without inventing additional eligibility
rules. Broker capability data remains covered by Story `0051` and must be
integrated before claiming complete account-specific market differentiation.
