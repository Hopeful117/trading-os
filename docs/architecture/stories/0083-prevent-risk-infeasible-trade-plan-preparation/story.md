# Story 0083 - Prevent Risk-Infeasible Trade Plan Preparation

## Metadata

**ID:** `0083`
**Title:** Prevent Risk-Infeasible Trade Plan Preparation
**Status:** Draft

---

## Goal

Prevent a trader from entering a TradePlan journey when the candidate plan
cannot reach a valid deterministic Risk outcome with the current account and
portfolio facts.

The trader should not prepare, accept, and review a plan only to discover that
it was already impossible because of blocking `DAILY_DRAWDOWN`,
`MAX_EXPOSURE`, or equivalent deterministic constraints.

This Story improves the decision flow without weakening or changing the
authoritative Risk rules.

---

## Context

Story `0082` streamlined the primary journey:

```text
Opportunity row
  -> create or reuse TradePlan
  -> human acceptance
  -> deterministic Risk evaluation
  -> explicit execution authorization
```

The current Risk engine correctly rejected a PAPER TradePlan with:

```text
DAILY_DRAWDOWN — daily-drawdown
MAX_EXPOSURE — maximum-exposure
```

The rejection was based on the authoritative account and portfolio snapshot,
not on a calculation defect. The current journey nevertheless lets the trader
prepare and accept a plan before discovering that outcome.

Relevant implementation surfaces include:

* `trading-os-web/src/app/features/opportunities`
* `trading-os-web/src/app/features/trade-planning`
* `trading-core/src/main/java/com/hope/trading/trading_core/risk`
* `risk-domain/src/main/java/com/hope/trading/risk`
* `market-intelligence` Opportunity TradePlan generation

---

## Problem

TradePlan preparation currently answers whether a plan can be constructed from
an Opportunity, but not whether the concrete candidate can pass the current
deterministic Risk constraints.

As a result, the user may:

1. open or create a TradePlan;
2. explicitly accept it;
3. wait for Risk evaluation;
4. receive a rejection that was predictable from the current account state.

This is a decision-flow design defect, not permission to bypass Risk or to
change the configured limits.

---

## Scope

* Introduce an authoritative feasibility check before or during TradePlan
  preparation, using the actual candidate plan and current account/portfolio
  facts.
* Prevent the primary Opportunity journey from presenting a plan as actionable
  when blocking Risk constraints already make it infeasible.
* Preserve the existing deterministic Risk rules and their configured limits.
* Surface blocking rule codes, metrics, and a truthful explanation when
  preparation is refused.
* Preserve retry behavior so the user can retry after portfolio, drawdown, or
  account facts have changed.
* Preserve TradePlan reuse: an existing TradePlan at any lifecycle stage must
  remain openable and must not be silently replaced by a feasibility check.
* Preserve explicit human acceptance and separate execution authorization.
* Add regression tests for feasible candidates, infeasible candidates, stale or
  unavailable facts, and existing-plan reuse.

---

## Out of Scope

* Changing `DAILY_DRAWDOWN`, `MAX_EXPOSURE`, `MAX_POSITION_RISK`, or any other
  Risk rule or default limit.
* Ignoring current open positions, current losses, or portfolio exposure.
* Automatically reducing quantity, changing the stop, changing the account, or
  silently replanning the candidate.
* Automatically accepting, authorizing, or executing a TradePlan.
* Adding a general historical TradePlan search/filter interface.
* Changing Opportunity scoring, ranking, expiration, or provenance.
* Replacing deterministic Risk with frontend calculations or AI output.
* Introducing a new broker-specific risk contract.

---

## Acceptance Criteria

* [ ] From an active Opportunity, the primary journey checks feasibility using
      the actual candidate TradePlan and the authoritative current account and
      portfolio facts.
* [ ] A candidate that deterministically exceeds `DAILY_DRAWDOWN`,
      `MAX_EXPOSURE`, or another blocking Risk rule is not presented as an
      actionable new TradePlan.
* [ ] An infeasible candidate produces a truthful, retryable response showing
      the blocking rule code and relevant authoritative metrics.
* [ ] Missing, stale, inconsistent, or unavailable Risk facts fail closed and
      do not produce a false feasibility success.
* [ ] A candidate that is feasible under the captured Risk facts can continue
      through the existing TradePlan acceptance and Risk evaluation flow.
* [ ] Existing TradePlans are reusable and openable at every lifecycle stage;
      the new feasibility check does not replace or invalidate them.
* [ ] No Risk rule, configured limit, account ownership check, or execution
      authorization boundary is weakened.
* [ ] Retry after a changed account or portfolio snapshot re-evaluates current
      facts rather than replaying a stale feasibility result.
* [ ] Frontend behavior does not compute or duplicate Risk calculations; it
      renders the authoritative server response.
* [ ] Tests cover `DAILY_DRAWDOWN`, `MAX_EXPOSURE`, feasible candidates, missing
      facts, retry, and existing-plan reuse.
* [ ] Applicable backend tests, Angular tests, production build, formatting,
      and `git diff --check` pass.

---

## Constraints

* Risk remains deterministic, fail-closed, and authoritative.
* The same inputs must produce the same feasibility result.
* Account and portfolio facts must be captured with explicit provenance and
  freshness.
* The frontend must not infer feasibility from balances, positions, or local
  calculations.
* Human acceptance remains explicit.
* Execution authorization remains a separate explicit action.
* Existing TradePlan lifecycle and idempotency behavior must remain compatible.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Trading OS Vision and Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-027.md` - Trade Planning Model
* `docs/architecture/adr/ADR-031.md` - Trade Planning and Risk Context Responsibilities
* `docs/architecture/adr/ADR-043.md` - Account and BrokerAccount Identity
* `docs/architecture/adr/ADR-044.md` - Inter-Service Trust and Actor Propagation

---

## Relevant Stories

* `0001` - Connect TradePlan to Risk
* `0003` - Authorize TradePlans through Risk Domain
* `0023` - Opportunity TradePlan Risk Decision
* `0030` - Connect Risk Decision to Human-Controlled Execution
* `0055` - PAPER TradePlan Risk-Compatible Sizing
* `0082` - Streamline Opportunity Trade Preparation and Risk Review

---

## Validation

* Unit and application tests for deterministic feasibility outcomes.
* Contract tests for authoritative account/portfolio facts and blocking reasons.
* Angular tests for refusal rendering, retry, direct navigation, and existing
  plan reuse.
* Authenticated PAPER validation with:
  * a feasible Opportunity;
  * an Opportunity blocked by `MAX_EXPOSURE`;
  * an Opportunity blocked by `DAILY_DRAWDOWN`;
  * changed facts followed by a successful retry.
* Production build, formatting, `git diff --check`, and applicable Maven tests.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved when required
* [ ] Implementation completed
* [ ] Documentation reconciliation completed
* [ ] Code review completed
* [ ] Deterministic and frontend validation completed
* [ ] Authenticated PAPER validation completed
* [ ] Human engineering approval completed
