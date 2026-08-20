# Story

## Metadata

**ID:** `0004`

**Title:** Validate Authorized Trade Plans before Execution

**Status:** Completed

---

## Goal

Complete the transition between deterministic risk authorization and trade
execution by introducing an explicit human validation step and creating
Execution Intents only from authoritative Trade Plans and persisted Risk
Evaluations.

---

## Context

Story 0003 completed the deterministic authorization pipeline by evaluating
immutable Trade Plans through the Risk Domain.

Trading Core is now capable of assembling authoritative RiskEvaluationContext
snapshots, invoking the Risk Domain and persisting immutable authorization
decisions.

The next step is to allow an authenticated user to explicitly validate an
authorized Trade Plan before it enters the execution lifecycle described by
ADR-029.

Trading Core must load and verify the authoritative Trade Plan, RiskEvaluation,
account and BrokerAccount before creating an Execution Intent.

No broker order placement is part of this Story.

---

## Problem

Authorized Trade Plans cannot yet continue safely into the execution lifecycle.

The current execution entry point may accept risk or execution information
supplied by the caller instead of rebuilding the execution request from
authoritative persisted data.

The platform also lacks an explicit, persisted and auditable human validation
step between risk authorization and execution.

Without this step, a caller could attempt to create an Execution Intent using
an invalid RiskEvaluation, a different Trade Plan version, unauthorized account
resources or execution parameters that were not evaluated by the Risk Domain.

---

## Scope

* Introduce explicit human validation for authorized Trade Plans.
* Load authoritative Trade Plans and RiskEvaluations inside Trading Core.
* Verify exact Trade Plan and RiskEvaluation version correspondence.
* Verify account and BrokerAccount ownership.
* Reject non-authorized or unavailable RiskEvaluation outcomes.
* Persist immutable human validation decisions.
* Create Execution Intents from authoritative Trade Plan data.
* Implement idempotent validation and Execution Intent creation.
* Preserve traceability between Trade Plan, RiskEvaluation, validation and
  Execution Intent.
* Expose a validation REST endpoint.
* Add targeted tests for the validation-to-execution flow.

---

## Out of Scope

* Broker order placement.
* Kraken integration changes.
* Automatic trade execution.
* Position monitoring.
* Trade Plan generation.
* Risk Domain rule changes.
* AI approval or autonomous validation.
* Frontend execution workflow.
* Modification of Trade Plan parameters during validation.

---

## Acceptance Criteria

* [ ] Authorized Trade Plans can be explicitly validated by an authenticated
  user.
* [ ] Trading Core loads the authoritative persisted RiskEvaluation.
* [ ] Trading Core loads the exact Trade Plan version referenced by the
  RiskEvaluation.
* [ ] Only authorized RiskEvaluation outcomes can continue to execution.
* [ ] Rejected, unknown or unavailable outcomes cannot create an Execution
  Intent.
* [ ] Account ownership is verified before validation.
* [ ] BrokerAccount ownership is verified before Execution Intent creation.
* [ ] Human validation decisions are immutable and persisted.
* [ ] Execution Intents are created from authoritative Trade Plan data.
* [ ] Callers cannot override the official risk decision.
* [ ] Callers cannot provide unvalidated execution parameters.
* [ ] Validation and Execution Intent creation are idempotent.
* [ ] Traceability from Trade Plan to RiskEvaluation, validation and Execution
  Intent is preserved.
* [ ] Relevant tests pass.
* [ ] No broker order is placed.
* [ ] No unrelated behavior is changed.

---

## Constraints

* Preserve existing service responsibilities.
* Respect ADR-028, ADR-029 and ADR-031.
* Keep risk and execution authorization deterministic.
* Trading Core owns authoritative validation and Execution Intent creation.
* Human validation cannot override the Risk Domain decision.
* RiskEvaluation data must never be trusted from client input.
* Execution parameters must originate from the authoritative Trade Plan.
* A modified Trade Plan version requires a new RiskEvaluation.
* BrokerAccount identifiers must be validated against the authenticated user.
* Trading Core must not access or persist broker credentials.
* Risk Domain never accesses Broker Service directly.
* Broker Service remains a provider of facts and execution capabilities only.
* Avoid provider-specific leakage outside infrastructure adapters.
* REST controllers must return `ResponseEntity`.
* Do not introduce unrelated dependencies.
* Do not commit, push, or merge automatically.

---

## Relevant ADRs

* `ADR-028 — Deterministic Risk Domain`
* `ADR-029 — Trade Execution Lifecycle`
* `ADR-031 — Clarify Trade Planning Context and Risk Context Responsibilities`

---

## Relevant Modules

* `trading-core`
* `risk-domain`
* `broker-service`
* `gateway`

---

## Validation

Expected validation:

* targeted Maven tests for `trading-core`;
* targeted Maven tests for `risk-domain`;
* targeted Maven tests for affected Gateway behavior;
* validation of Trade Plan and RiskEvaluation version matching;
* validation of account and BrokerAccount ownership;
* validation of idempotent human approval;
* validation of idempotent Execution Intent creation;
* end-to-end validation from authorized Trade Plan to Execution Intent;
* architecture validation against ADR-028, ADR-029 and ADR-031;
* manual review in IntelliJ;
* verification that no broker order is placed.

---

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved when required.
* [ ] Implementation completed.
* [ ] Relevant validation executed.
* [ ] Diff reviewed in IntelliJ.
* [ ] Code Review approved.
* [ ] Engineering Report completed.
* [ ] Human commit created.
