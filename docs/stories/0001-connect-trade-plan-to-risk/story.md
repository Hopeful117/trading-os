# Story

## Metadata

**ID:** `0001`

**Title:** Connect Trade Plan to Risk Evaluation

**Status:** Approved

---

## Goal

Connect an approved Market Intelligence Trade Plan to the deterministic Risk Domain.

The system must be able to evaluate whether a Trade Plan respects the applicable risk rules before any execution intent or broker order is created.

---

## Context

Trading OS already contains:

* Market Intelligence observations;
* ranked trading opportunities;
* deterministic and AI-assisted Trade Plans;
* an autonomous deterministic Risk Domain;
* an Execution Domain in Trading Core;
* Broker Service execution capabilities.

These components are not yet connected through the complete decision pipeline.

The intended workflow is:

```text
Opportunity
    ↓
Trade Plan
    ↓
Risk Evaluation
    ↓
Human Validation
    ↓
Execution Intent
```

This Story covers only the transition from Trade Plan to Risk Evaluation.

---

## Problem

Trade Plans currently exist independently from the deterministic Risk Domain.

A user or downstream workflow cannot yet request a risk evaluation for a specific Trade Plan and receive a structured result explaining whether the plan is acceptable.

Without this connection, Trading OS cannot safely continue toward human validation and execution.

---

## Scope

Implement the minimum application flow required to:

* select an existing Trade Plan;
* load the account and risk context required for evaluation;
* translate the Trade Plan into the input expected by the Risk Domain;
* execute the deterministic risk evaluation;
* return a structured risk result;
* preserve traceability between the Trade Plan and the risk evaluation;
* expose the result through an appropriate application API;
* cover the integration with focused automated tests.

The Repository Analysis must determine the correct owning service and existing extension points before implementation.

---

## Out of Scope

* Broker order placement
* Creation of an Execution Intent
* Human approval workflow
* Automatic trade execution
* Kraken sandbox validation
* AI model integration
* News or macroeconomic analysis
* Frontend implementation
* New risk algorithms
* Changes to broker architecture
* Changes to market-data collection
* New microservices

---

## Acceptance Criteria

* [ ] An existing Trade Plan can be submitted for deterministic risk evaluation.
* [ ] The evaluation uses the current account and applicable risk context.
* [ ] The Risk Domain remains the sole authority for deterministic risk decisions.
* [ ] Market Intelligence does not execute orders or make broker calls.
* [ ] No Execution Intent is created by this Story.
* [ ] The result clearly distinguishes an accepted evaluation from a rejected evaluation.
* [ ] A rejected result contains structured reasons suitable for later display.
* [ ] The result remains traceable to the evaluated Trade Plan.
* [ ] Missing or invalid inputs produce explicit controlled errors.
* [ ] Repeating the same evaluation does not create unintended execution side effects.
* [ ] Existing Market Intelligence, Risk Domain, and Trading Core tests remain green.
* [ ] Focused tests cover the new application flow.
* [ ] No unrelated module is modified.

---

## Constraints

* Preserve the responsibilities defined by accepted ADRs.
* Keep risk evaluation fully deterministic.
* Do not duplicate Risk Domain rules in Market Intelligence or Trading Core.
* Do not allow Market Intelligence to access Broker Service directly.
* Do not create or submit broker orders.
* Do not introduce a new microservice.
* Reuse existing domain models and ports when appropriate.
* Keep public contracts independent from Kraken.
* REST controllers must return `ResponseEntity`.
* Do not commit, push, merge, or discard pre-existing user changes.
* Stop and report the conflict if implementation requires an unapproved architectural decision.

---

## Relevant ADRs

* ADR-003 — AI-first architecture
* ADR-004 — Market Intelligence
* ADR-005 — Trading Core
* ADR-009 — Risk Rules
* ADR-014 — Decision Pipeline
* ADR-020 — Market Intelligence Foundation
* ADR-022 — Artifact Memory
* ADR-023 — Capabilities and DAG Planning
* ADR-026 — Opportunities
* ADR-027 — Trade Planning
* ADR-028 — Risk Domain
* ADR-029 — Execution Domain

The Repository Analysis must verify the exact repository paths and current contents of these ADRs.

---

## Relevant Modules

* `market-intelligence`
* `risk-domain`
* `trading-core`

The Repository Analysis must determine whether the orchestration belongs in Market Intelligence or Trading Core according to the accepted ADRs and current code.

---

## Validation

At minimum:

* targeted Market Intelligence tests;
* targeted Risk Domain tests;
* targeted Trading Core tests when modified;
* tests for accepted and rejected evaluations;
* tests for missing or invalid context;
* tests proving that no Execution Intent or broker request is created;
* `git diff --check`.

Each service must be tested independently because the repository has no root Maven aggregator.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved only if required
* [ ] Implementation delegated to the coding agent
* [ ] Relevant automated tests pass
* [ ] No broker execution is triggered
* [ ] Diff reviewed in IntelliJ
* [ ] Human corrections completed
* [ ] Code Review approved
* [ ] Engineering Report completed
* [ ] Human commit created
