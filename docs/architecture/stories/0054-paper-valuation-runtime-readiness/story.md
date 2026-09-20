# Story 0054 - Make PAPER market valuation available for runtime risk evaluation

## Metadata

**ID:** `0054`
**Title:** Make PAPER market valuation available for runtime risk evaluation
**Status:** Approved

---

## Goal

Allow an authenticated user to evaluate a valid PAPER Trade Plan through the
web application when the selected market has a recent, compatible valuation.

The deterministic risk boundary must remain fail-closed when valuation data is
missing or stale. This Story addresses the runtime data path; it does not
weaken risk validation or introduce synthetic prices.

---

## Context

During the Story 0048 runtime walkthrough, account onboarding and Trade Plan
creation succeeded for a PAPER account. Deterministic risk evaluation then
returned:

```text
CURRENT_MARKET_VALUATION_UNAVAILABLE
```

The failure stopped the supported journey before explicit execution
authorization. The current uncertainty is whether the selected market data,
freshness policy, service routing, or runtime configuration prevents Trading
Core from receiving an authoritative valuation.

---

## Problem

The product can display an active opportunity and create a Trade Plan, but the
runtime cannot currently prove that the corresponding PAPER market valuation is
available at risk-evaluation time. Without that fact, risk must refuse the
decision and the normal PAPER journey cannot continue to execution.

---

## Scope

* Trace the valuation path from the selected market through Market Data and
  Trading Core risk evaluation.
* Make the supported runtime configuration provide a recent valuation for a
  valid tradable PAPER market.
* Preserve explicit freshness, availability, and currency compatibility rules.
* Preserve deterministic risk behavior and fail-closed handling of missing or
  stale valuations.
* Validate the result through the existing web application flow.
* Add focused automated coverage for the discovered deterministic regression.
* Produce runtime evidence that can unblock the Story 0048 walkthrough.

---

## Out of Scope

* Changing risk thresholds, position-sizing rules, or approval policy.
* Adding synthetic, fallback, or manually injected market prices.
* LIVE broker or Kraken sandbox acceptance.
* Autonomous execution or unattended trading.
* Introducing a new end-to-end test framework.
* Redesigning Market Intelligence strategies or opportunity ranking.
* Hiding `CURRENT_MARKET_VALUATION_UNAVAILABLE` from the user.

---

## Acceptance Criteria

* [ ] A supported tradable PAPER market has a recent valuation available through
      the configured Market Data runtime path.
* [ ] The valuation is compatible with the account currency and the Trade Plan
      instrument.
* [ ] A valid PAPER Trade Plan reaches deterministic risk evaluation without
      `CURRENT_MARKET_VALUATION_UNAVAILABLE` when the valuation is recent.
* [ ] Missing or stale valuation data still produces an explicit fail-closed
      decision and does not permit execution.
* [ ] The user can reach the valuation-backed risk result from the web
      application without manually constructing an API request.
* [ ] No live credentials, live order, security bypass, or synthetic price is
      used by the validation scenario.
* [ ] Focused Market Data, Trading Core, and frontend regression tests pass.
* [ ] The Angular production build passes.
* [ ] Runtime evidence identifies the market, quote timestamp/freshness,
      observed risk result, and any remaining blocker.

---

## Constraints

* Use existing Market Data, Trading Core, Gateway, and frontend contracts where
  they are compatible with the approved architecture.
* Preserve the deterministic risk and human authorization boundaries.
* Do not make a missing valuation look current.
* Use an isolated PAPER account and non-production credentials.
* Any required change to market-data ownership, risk semantics, or service
  boundaries requires a separate approved architectural decision.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Trading OS Vision and Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-028.md` - Deterministic Risk
* `docs/architecture/adr/ADR-029.md` - Execution Domain Architecture
* `docs/architecture/adr/ADR-044.md` - Service-to-Service Trust and Actor Propagation

---

## Relevant Stories

* `docs/architecture/stories/0048-paper-trading-journey-runtime-acceptance/story.md`
* `docs/architecture/stories/0041-paper-local-position-query-valuation/story.md`
* `docs/architecture/stories/0050-paper-risk-context/story.md`
* `docs/architecture/stories/0053-effective-paper-planning-profile/story.md`

---

## Relevant Modules

* `market-data`
* `trading-core`
* `gateway`
* `trading-os-web`
* `risk-domain`

---

## Validation

* Focused Market Data and Trading Core tests for valuation freshness and
  propagation.
* Relevant Angular `npm run test:ci` coverage.
* Angular `npm run build`.
* Authenticated PAPER runtime walkthrough through the web application.
* Negative runtime check for missing or stale valuation data.
* `git diff --check` and review of runtime evidence.

---

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Runtime scenario approved.
* [ ] Implementation or regression fix completed within scope.
* [ ] Relevant validation executed.
* [ ] Story 0048 can continue past deterministic risk evaluation, or the
      remaining blocker is documented with evidence.
* [ ] Diff reviewed in IntelliJ.
* [ ] Code Review approved.
* [ ] Human commit created.
