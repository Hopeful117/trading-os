# Story 0022 — Trigger a market scan from the product

## Metadata

**ID:** `0022`

**Title:** Trigger an Active Market Scan from Trading OS Web

**Status:** Approved

---

## Goal

An authenticated trader can launch an Active Market Scan from the Trading OS
Angular app against the already-implemented Market Intelligence pipeline,
understand what the scan is doing while it runs and what it produced when it
finishes (including the legitimate "completed with zero opportunities"
outcome), and reach the produced opportunities through the existing
Opportunities surface.

```text
Trader → Trading OS Web → Active Scan API → Market Intelligence
       → deterministic strategy evaluation → StrategyMatch
       → TradingOpportunity → existing Opportunities UI
```

This closes V1 Definition of Done closure blocker #3 (journey step 4: "trigger
market analysis") and makes the Market Intelligence loop usable end-to-end
from the product.

---

## Context

* Story 0021 exposed ACTIVE opportunities in Angular (`/opportunities`) but the
  surface is passive: nothing produces opportunities unless someone calls the
  scan API with developer tooling.
* The backend Active Scan capability is fully implemented (Stories 0005–0007):
  persisted scans, scope resolution with ownership checks, deterministic
  dispatch, per-market analysis, result projection with truthful strategy
  provenance.
* The public API contract exists and is routed by the Gateway since Story 0019;
  the Gateway injects `X-Actor-Id` from the validated JWT
  (`AuthenticatedActorHeaderFilter`).

---

## Problem

Triggering a scan today requires curl/Swagger and knowledge of internal
contracts. Journey step 4 is impossible through the product: the trader cannot
ask "is there something now?".

---

## Scope

* Typed frontend models for the Active Scan contract actually served
  (`CreateActiveScanRequestDto`, `ActiveScanResponse`, lifecycle statuses,
  progress, per-market results).
* An `ActiveScanService` consuming `POST /api/v1/intelligence/scans` and
  `GET /api/v1/intelligence/scans/{scanId}`.
* A scan panel embedded in the existing Opportunities page: account selection
  (required by contract), optional objective, single trigger action.
* Bounded polling of the scan projection until a terminal backend status,
  with explicit running/success/partial/failed/no-work states driven by real
  backend values.
* Double-trigger protection (UI disabled + RxJS semantics).
* Navigation to the existing opportunities list on completion; no duplicated
  opportunity rendering.
* Unit tests for all implemented behaviors.

---

## Out of Scope

* Any backend change (Market Intelligence, Gateway, Trading Core) — audit
  concluded the public API is sufficient (see repository-analysis.md).
* Market multi-selection UI (backend default resolves the full eligible
  catalogue; requestedMarketIds stays unused by the UI in this story).
* Passive scanner, scheduled scans, scan history dashboard, scan presets.
* Trade plan creation, risk decision, execution surfaces.
* Per-market results drill-down (only aggregate progress is displayed).
* AI wording of any kind; footer "AI Engine" placeholder remains untouched.

---

## Acceptance Criteria

* [ ] Authenticated trader triggers a scan from the Opportunities page with an
      account selected from their own accounts; request carries an
      Idempotency-Key and reaches the backend through the public Gateway.
* [ ] While running, a clear non-fake running state is shown (no invented
      percentages); polling stops deterministically at terminal status.
* [ ] Success, partial success, failure and completed-without-work are
      distinguishable, using only backend-provided statuses and counts;
      "zero eligible markets" and "zero opportunities matched" are not shown as
      errors.
* [ ] Double-click / repeat clicks cannot start concurrent scan sessions
      (exhaustMap + disabled control).
* [ ] Trigger/validation/unauthorized/conflict and network errors are visible
      without leaking stack traces.
* [ ] On terminal completion the existing ACTIVE opportunities list is
      refreshed; no second opportunity representation is created.
* [ ] All 184 existing frontend tests stay green; new behaviors are tested;
      `npm run test:ci` and `npm run build` pass; Prettier check passes.
* [ ] No unrelated behavior changed; zero backend file modifications.

---

## Constraints

* Deterministic analysis stays backend-owned; Angular only expresses the
  trader command and displays returned truth.
* Respect ADR-033 (active/passive orchestration), ADR-023 (capability
  execution), ADR-034/038 (provenance honesty), Story 0019 routing patterns.
* Reuse authGuard/JWT/Gateway flows; no new security mechanism.
* Follow Story 0021 reactive conventions (discriminated view models +
  async pipe, no manual subscriptions).

---

## Relevant ADRs

* `docs/architecture/adr/ADR-033.md` — Active and Passive Market Intelligence Orchestration
* `docs/architecture/adr/ADR-023.md` — Capability Execution Model
* `docs/architecture/adr/ADR-034.md` — Strategy, StrategyMatch and Trading Opportunity Boundaries
* `docs/TRADING_OS_V1_TRADER_DEFINITION_OF_DONE.md` — journey-order prioritization
