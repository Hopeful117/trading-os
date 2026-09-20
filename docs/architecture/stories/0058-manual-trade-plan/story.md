# Story 0058 - Create Manual-Origin Trade Plans

## Metadata

**ID:** `0058`
**Title:** Create Manual-Origin Trade Plans
**Status:** IMPLEMENTATION_COMPLETE - HUMAN REVIEW PENDING

---

## Goal

Allow an authenticated human trader to create a Trade Plan without a generated
Trading Opportunity while preserving the existing Trade Plan, Risk Evaluation,
human authorization and Execution Intent invariants.

Both opportunity-driven and discretionary entries must use the same normalized
Trade Plan concept:

```text
OPPORTUNITY Trade Plan
MANUAL Trade Plan
        ↓
Risk Evaluation
        ↓
Human validation
        ↓
Execution Intent
```

This Story establishes the manual Trade Plan domain and application contract. It
does not yet add the complete direct-trading frontend or broker execution
journey.

---

## Context

The current Trade Plan creation contract requires at least one
`TradingOpportunity`. This supports the Market Intelligence workflow but blocks
discretionary trading when the human trader has identified a setup that was not
produced by the opportunity pipeline.

ADR-047 establishes that `TradePlan` remains the single normalized proposal
model and introduces two explicit origins:

```text
OPPORTUNITY
MANUAL
```

A manual Trade Plan is a proposal, not an execution command. It must continue
through deterministic Risk Evaluation and explicit human approval before an
Execution Intent can be created.

---

## Problem

The platform currently has no truthful representation for a discretionary trade
without an Opportunity. The unsafe alternatives are either:

* creating a synthetic Opportunity that misrepresents provenance; or
* creating a second manual execution path that could bypass risk, approval,
  idempotency or audit behavior.

The domain and transport contracts must support a manual origin without breaking
existing persisted opportunity-origin Trade Plans.

---

## Scope

### Included

* Add an explicit Trade Plan origin with `OPPORTUNITY` and `MANUAL` values.
* Allow a manual Trade Plan to have no Opportunity reference.
* Preserve the existing opportunity-origin behavior.
* Preserve immutable Trade Plan identity and version lineage.
* Capture the authenticated author of a manual Trade Plan.
* Preserve the account and planning-context references required for traceability.
* Support the existing Trade Plan execution parameters without adding new order
  types.
* Validate manual and opportunity-origin combinations and reject invalid
  combinations deterministically.
* Preserve the existing Risk Evaluation and Execution Intent contracts.
* Keep broker-specific fields outside the Trade Plan model.
* Add persistence, transport and regression tests required by the contract.

### Manual plan semantics

* `MANUAL` plans do not require a `TradingOpportunity`.
* `OPPORTUNITY` plans retain their current Opportunity provenance requirements.
* A manual rationale may be stored as explanatory user input, but it is never a
  risk authorization or a replacement for deterministic risk facts.
* Manual plans retain planning context for audit and decision provenance.

---

## Out of Scope

* Direct-trading frontend workflow.
* Risk-rule or risk-policy changes.
* New sizing algorithms.
* Bypassing human validation.
* Creating Execution Intent directly from frontend input.
* Direct broker calls from the manual-plan endpoint.
* New broker order types.
* Stop orders, partial fills or pending-order simulation.
* Partial close or full position close changes.
* Stop-loss or take-profit modification.
* Automatic or unattended trading.
* Synthetic Opportunities for manual plans.
* Introducing a persistent internal Position UUID.

---

## Acceptance Criteria

* [x] A Trade Plan has an explicit origin represented in its domain and
      persistence model.
* [x] Existing opportunity-origin Trade Plans remain readable and preserve
      their Opportunity references after migration.
* [x] An `OPPORTUNITY` Trade Plan cannot be created without the required
      Opportunity provenance.
* [x] A `MANUAL` Trade Plan can be created without a Trading Opportunity.
* [x] A manual Trade Plan records the authenticated author and cannot use a
      client-provided actor identifier as its authorization authority.
* [x] A manual Trade Plan retains the account and planning-context references
      required by the existing architecture.
* [x] A manual Trade Plan contains valid instrument, direction and supported
      execution parameters.
* [x] Invalid manual-plan input is rejected before persistence with stable,
      actionable validation errors.
* [x] Manual and opportunity-origin Trade Plans use the same immutable identity,
      version and lifecycle rules.
* [x] No manual Trade Plan creation path calls a broker or creates an Execution
      Intent.
* [x] Risk Evaluation continues to be required before any Execution Intent can
      be created.
* [x] The Trade Plan model remains independent of broker-specific concepts.
* [x] API and persistence tests cover both origins, migration compatibility and
      invalid origin/provenance combinations.
* [x] Affected Market Intelligence and Trading Core tests pass.
* [x] `git diff --check` passes.

---

## Constraints

* ADR-047 is authoritative for the manual-origin decision.
* ADR-027, ADR-028, ADR-029 and ADR-031 remain authoritative for Trade Plan,
  risk and execution boundaries.
* ADR-044 remains authoritative for authenticated ownership and actor identity.
* Existing opportunity-origin API behavior must not be changed incompatibly
  without an explicit migration strategy.
* Existing persisted Trade Plans must remain readable.
* Market Intelligence remains the owner of the Trade Plan concept and lifecycle.
* Trading Core remains the owner of account authorization, risk evaluation and
  Execution Intent creation.
* The frontend and client-provided identifiers are not authoritative for account
  ownership, risk or execution authorization.
* This Story must not weaken deterministic risk controls or human authority.

---

## Architectural Decision

`NEW_ADR_REQUIRED = NO`.

ADR-047 already establishes the required architecture. A new ADR is required
only if implementation discovers that manual Trade Plans require a new domain
owner, a second execution pipeline, a different risk authority or a change to
the accepted decision pipeline.

---

## Relevant ADRs

* `ADR-001` - Trading OS Vision and Human Authority
* `ADR-014` - Trading Decision Pipeline
* `ADR-027` - Trade Planning Architecture
* `ADR-028` - Deterministic Risk
* `ADR-029` - Execution Domain Architecture
* `ADR-031` - Trade Planning Context and Risk Context Responsibilities
* `ADR-044` - Inter-Service Trust and Actor Propagation
* `ADR-047` - Manual Trade Plans as a First-Class Trade Plan Origin

---

## Related Stories

* `0003` - Authorize Trade Plans Through Risk Domain
* `0023` - Opportunity Trade Plan Risk Decision
* `0046` - Trade Plan Frontend State and Error Resilience
* `0055` - Make PAPER Trade Plan Sizing Compatible with Effective Risk

---

## Relevant Modules

* `market-intelligence`
* `trading-core`
* `risk-domain` only if the existing transport contract requires a compatible
  read-model update

---

## Validation

* Trade Plan domain tests for both origins.
* Trade Plan persistence tests including existing opportunity-origin data.
* API contract tests for manual and opportunity-origin creation.
* Trading Core client and risk-handoff regression tests.
* Negative tests proving that manual creation does not create an Execution
  Intent or call a broker.
* `git diff --check`.

---

## Definition of Done

* [x] Repository Analysis approved.
* [x] Implementation Plan approved when required.
* [x] Story scope approved.
* [x] Implementation completed within this Story's scope.
* [x] Acceptance criteria validated with evidence.
* [ ] Human code review completed.
* [ ] Human commit created.
