# Story 0023 — Decide a proposed Trade Plan from an Opportunity

## Metadata

**ID:** `0023`

**Title:** Decide a proposed Trade Plan from an Opportunity (accept → deterministic risk decision)

**Status:** Approved

---

## Goal

From an ACTIVE `TradingOpportunity`, an authenticated trader can prepare a
`TradePlan` using the existing Market Intelligence aggregate, review the
proposed plan (parameters, sizing, rationale, provenance), explicitly ACCEPT
or REJECT it, run the existing deterministic risk evaluation and read the
persisted decision verbatim. The flow stops after the risk decision — no
broker execution surface exists in this story.

---

## Context

* Stories 0021/0022 made opportunities visible and scannable from the product.
* The boundary investigation (`docs/architecture/reports/opportunity-to-trade-plan-investigation.md`)
  established that the full intention machinery already exists: versioned
  `TradePlan` aggregate, lifecycle policy, planning engine with deterministic
  policies, Trading Core risk orchestration and a 14-check execution gate.
* Missing seams: public opportunity-based creation, explicit human decision
  API, any plan/risk UI. A known sequencing divergence between
  RISK_VALIDATED (post-acknowledgment) and the execution gate's ACCEPTED
  snapshot precondition belongs to the NEXT story (execution).

---

## Problem

The trader cannot form a trade intention through the product: proposed plans
cannot be created from an opportunity publicly, reviewed, accepted/rejected,
or evaluated against risk rules. The human-validation boundary demanded by
the V1 Definition of Done is modeled in the domain but absent from the APIs
and UI.

---

## Scope

* Public creation of a PROPOSED plan from an ACTIVE opportunity, orchestrated
  by Trading Core over a new internal Market Intelligence endpoint (planning
  inputs entirely deterministic: opportunity + fresh market price + effective
  trading profile).
* Explicit actor-bound accept/reject capability on the existing lifecycle
  (PROPOSED → ACCEPTED / REJECTED) with owner, latest-version and
  repeated-command semantics.
* Read access to a plan version for the owner (proposal review).
* Reuse of the existing risk evaluation endpoint unchanged.
* Angular `trade-planning` feature: preparation form (account only), proposal
  view with provenance, accept/reject actions, risk-decision rendering
  (APPROVED / APPROVED_WITH_WARNINGS / REJECTED with reasons).
* Backend and frontend tests for every new seam.

---

## Out of Scope

* Any broker execution surface (`/executions/**`, ExecutionIntent UI, Kraken).
* Resolving the RISK_VALIDATED-vs-ACCEPTED execution-gate divergence (Gap #3)
  or wiring READY_TO_EXECUTE/recordExecuted.
* Legacy `Trade` integration; replan UI; plan history browser; advanced order
  ticket; strategy configuration; AI contributions beyond governance as-is.
* Risk Engine changes of any kind; persistent idempotency storage for the new
  creation path (documented limitation below).

---

## Acceptance Criteria

* [ ] Authenticated trader creates a PROPOSED plan from an ACTIVE opportunity
      through the public Gateway; instrument/direction come from the
      opportunity; entry/SL/TP/sizing derive deterministically server-side;
      provenance (opportunity → strategy match) is preserved and displayed.
* [ ] Trader explicitly accepts or rejects the proposed plan; the decision is
      actor-bound, latest-version-checked, lifecycle-policy-guarded; repeated
      identical decisions are idempotent-success; conflicting decisions and
      stale versions fail explicitly.
* [ ] After acceptance, the trader triggers the EXISTING risk evaluation
      endpoint; the persisted decision (incl. APPROVED_WITH_WARNINGS nuance
      and refusal reasons) renders verbatim; REJECTED shows no progression.
* [ ] No execution action exists anywhere in the flow, even when APPROVED.
* [ ] Expired opportunity/plan, wrong owner, unknown account produce explicit
      backend errors rendered honestly.
* [ ] Frontend baseline (205 tests) stays green; new behaviors tested
      including state-machine transitions and double-trigger protection.
* [ ] Backend modules touched pass their Maven quality gates; Prettier clean;
      `git diff --check` clean.

---

## Constraints

* Reuse the existing `TradePlan` aggregate — no parallel intention concept.
* Do not touch legacy `Trade`.
* Preserve responsibility split: MI owns planning state; Trading Core owns the
  public application boundary and risk orchestration; risk-domain owns
  evaluation; Angular expresses intent and renders results.
* Identity from JWT/Gateway mechanisms only; server-side ownership checks.
* Deterministic authority: frontend never computes risk or sizing.
* Respect natural acknowledgment semantics (approved evaluation →
  RISK_VALIDATED); do not defer acknowledgment to please the future execution
  gate.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-027.md` — Trade Planning Model
* `docs/architecture/adr/ADR-031.md` — Trade Planning vs Risk Context responsibilities
* `docs/architecture/adr/ADR-032.md` — Entry Intent
* `docs/TRADING_OS_V1_TRADER_DEFINITION_OF_DONE.md` — human-authority rules
