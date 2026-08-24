# Story 0021 — Surface active Market Intelligence opportunities in the Web app

## Metadata

**ID:** `0021`

**Title:** Surface active Market Intelligence opportunities in the Web app

**Status:** Approved

---

## Goal

An authenticated trader can open Trading OS Web, see the Market Intelligence
opportunities that are currently ACTIVE, and open one opportunity to understand
why it exists (status, validity window, strategy provenance, deterministic
explanation) — entirely through the product, without developer tooling.

This closes V1 Definition of Done closure blocker #2 (journey steps 5–6) and
delivers the first trader-visible output of the full intelligence chain:

```text
market data → active scan → strategy evaluation → StrategyMatch
    → TradingOpportunity → Gateway → Angular UI → Trader
```

---

## Context

* The backend produces persisted, versioned, expirable `TradingOpportunity`
  aggregates derived from required `StrategyMatch` truth (Stories 0011–0012,
  ADR-034).
* The expiration driver transitions opportunities out of ACTIVE
  (Story 0018), so ACTIVE is a backend-owned guarantee.
* Story 0019 made `/api/v1/opportunities/**` publicly reachable through the
  Gateway with no path rewrite.
* The resumption investigation (`docs/architecture/reports/trading-os-resumption-investigation.md`)
  confirmed the Angular app has zero Market Intelligence surface and identified
  this gap as the next journey-ordered blocker.
* The current chain is deterministic. No AI capability exists behind it; the
  product must not present deterministic output as AI-generated.

---

## Problem

A trader using Trading OS has no way to see that Market Intelligence produced
anything. Opportunities exist in the database and are served through the public
Gateway, but no screen consumes them. Journey steps 5 ("view active
opportunities") and 6 ("understand why an opportunity exists") are impossible
through the product.

---

## Scope

* Typed frontend models for the opportunity contract actually served by
  `GET /api/v1/opportunities/active` and `GET /api/v1/opportunities/{id}`.
* An `OpportunityService` (typed, reactive) consuming only these two endpoints.
* An opportunities list view showing currently ACTIVE opportunities with the
  fields the contract really provides (instrument, direction, type, timeframe,
  score, status, validity window, explanation excerpt, strategy provenance
  presence).
* An opportunity detail view presenting identity, market, status, validity,
  strategy provenance (`strategyMatchId` when present), observation references,
  timestamps and the deterministic explanation.
* Explicit loading, empty and error states on both views.
* Routes under the existing guard conventions plus one sidebar navigation entry.

---

## Out of Scope

* Scan triggering UI of any kind (no "Run scan" action).
* Trade-plan creation, risk-decision display, execution surface.
* Any AI-flavored UI (AI badges, generated explanations, confidence chat).
* Backend changes in any service, including Market Intelligence and Gateway.
* Opportunity version history view (`/history/{id}` endpoint left unused for now).
* Pagination/filter UI beyond what the ACTIVE endpoint returns.
* Bundle-budget remediation and legacy package cleanup.

---

## Acceptance Criteria

* [ ] Authenticated trader sees real persisted ACTIVE opportunities in the app;
      expired ones are never presented as active (guarantee stays backend-owned;
      frontend does not re-implement expiration rules).
* [ ] Opening an opportunity shows why it exists using deterministic backend
      data only (explanation, evidence references, strategy provenance).
* [ ] Loading, empty ("no active opportunities") and error states are explicit
      on list and detail views; errors are visible but never leak stack traces.
* [ ] Detail of a missing opportunity shows a truthful not-found state (HTTP 404).
* [ ] Routes require authentication via the existing guard; a sidebar entry
      navigates to the list.
* [ ] Frontend unit tests cover service, list states, detail states and
      navigation; `npm run test:ci` and `npm run build` pass without degrading
      the quality baseline.
* [ ] No unrelated behavior is changed; zero backend file modifications.

---

## Constraints

* Preserve existing service responsibilities and the responsibility chain
  (Market Intelligence interprets; this story only displays its output).
* Respect accepted ADRs, especially ADR-034 (provenance semantics),
  ADR-038 (honest validation status — never present UNVALIDATED as validated)
  and ADR-003/021 (no AI authority claims).
* Follow existing Angular conventions: standalone components, typed core
  services, Observables + async pipe, no manual subscriptions where a reactive
  flow works, no new state manager or UI framework.
* Display backend truth verbatim; do not recompute status, scores, validity or
  risk on the frontend.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-026.md` — Trading Opportunity Model
* `docs/architecture/adr/ADR-034.md` — Strategy, StrategyMatch and Trading Opportunity boundaries
* `docs/architecture/adr/ADR-038.md` — Empirical Strategy Validation Boundary
* `docs/TRADING_OS_V1_TRADER_DEFINITION_OF_DONE.md` — journey-order prioritization
