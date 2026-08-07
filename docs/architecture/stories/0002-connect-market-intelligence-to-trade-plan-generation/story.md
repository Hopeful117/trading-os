# Story

## Metadata

**ID:** `0002`

**Title:** Connect Market Intelligence to Trade Plan Generation

**Status:** Approved

---

## Goal

Connect the existing Market Intelligence capabilities into a coherent production flow that can transform completed market analysis into a persisted Trade Plan candidate.

The resulting flow must preserve deterministic traceability from source market data and analysis artifacts through observations, opportunities, trading context, and Trade Plan creation.

---

## Context

Trading OS already contains substantial Market Intelligence building blocks:

* public analysis execution;
* market-context assembly;
* deterministic analysis capabilities;
* capability artifacts;
* observations;
* opportunities;
* trading contexts;
* Trade Plan generation;
* durable Trade Plan versions;
* deterministic Trade Plan risk evaluation in Trading Core.

However, these capabilities currently form partially connected paths.

The public analysis flow produces analysis results, but it does not yet orchestrate the complete production chain:

```text
Market Data
    ↓
Analysis
    ↓
Artifacts
    ↓
Observations
    ↓
Opportunities
    ↓
Trading Context
    ↓
Trade Plan
```

Observations, opportunities, and trading contexts exist as domain and application capabilities, but they are not consistently created by the production analysis workflow.

---

## Problem

A user can request market analysis, and the system contains the domain logic required to create observations, opportunities, contexts, and Trade Plans.

However, no complete production workflow currently transforms one completed analysis into a traceable Trade Plan candidate.

As a result:

* analysis output remains disconnected from opportunity creation;
* observations are not systematically produced;
* opportunities are not systematically derived;
* trading contexts are not populated through the active workflow;
* Trade Plan creation cannot naturally begin from a completed analysis;
* the complete intelligent trading pipeline cannot operate end to end.

---

## Scope

Implement the smallest coherent production flow required to:

* consume a successfully completed Market Intelligence analysis;
* identify eligible completed capability results or artifacts;
* build one or more domain observations;
* persist observations through the repository abstraction used by production;
* create or update eligible trading opportunities;
* assemble the Trading Context required by Trade Planning;
* invoke the existing Trade Plan generation workflow;
* persist the resulting Trade Plan candidate and version;
* preserve traceability across:

    * analysis execution;
    * capability execution;
    * artifacts;
    * observations;
    * opportunity;
    * Trading Context;
    * Trade Plan;
* expose or reuse an appropriate application entry point for triggering or observing the flow;
* add focused automated tests for the connected production path.

The Repository Analysis must identify which existing orchestration stack should become authoritative before implementation.

---

## Out of Scope

* Risk evaluation changes
* Execution Intent creation
* Broker order execution
* Kraken sandbox validation
* Human Trade Plan approval UI
* Angular frontend implementation
* Real AI Engine integration
* News or macroeconomic contributors
* Passive scanner scheduling
* Distributed messaging
* New microservices
* Large redesign of Market Intelligence
* Rewriting existing observation, opportunity, or Trade Plan domain models
* Full replacement of all in-memory persistence unless required for the minimum coherent flow

---

## Acceptance Criteria

* [ ] A successfully completed analysis can trigger the production intelligence pipeline.
* [ ] Eligible completed analysis results or artifacts create domain observations.
* [ ] Invalid, incomplete, or failed capability results do not create observations.
* [ ] Observations preserve evidence and source traceability.
* [ ] Eligible observations can create or update a trading opportunity.
* [ ] Existing opportunity deduplication and versioning rules are preserved.
* [ ] A Trading Context is assembled from authoritative existing inputs.
* [ ] The existing Trade Planning application flow is invoked.
* [ ] A Trade Plan candidate is persisted with its immutable version.
* [ ] The Trade Plan remains traceable to its opportunity, observations, analysis, and artifacts.
* [ ] Reprocessing the same completed analysis does not create uncontrolled duplicates.
* [ ] Failed intermediate stages produce explicit controlled outcomes.
* [ ] The production path uses one authoritative orchestration model.
* [ ] Existing Market Intelligence tests remain green.
* [ ] Focused tests cover the complete connected path.
* [ ] No risk evaluation or broker execution is triggered.
* [ ] No unrelated service is modified.

---

## Constraints

* Preserve accepted ADR responsibilities.
* Market Intelligence owns intelligence production and Trade Plan proposal generation.
* Trading Core remains responsible for deterministic risk evaluation and execution orchestration.
* Do not duplicate observation, opportunity, or Trade Planning business rules.
* Reuse existing domain services, repositories, ports, and versioning behavior.
* Do not create a second parallel production workflow.
* Resolve the current overlap between the public analysis orchestrator and the ADR-023 capability engine with the smallest approved change.
* Preserve artifact and evidence provenance.
* Do not treat incomplete capability executions as valid intelligence.
* Do not introduce AI-generated facts as trusted domain knowledge.
* Keep the implementation incremental.
* Do not commit, push, merge, or discard pre-existing user changes.
* Stop and report the blocker if implementation requires an unapproved architectural decision.

---

## Relevant ADRs

* ADR-003 — AI-first architecture
* ADR-004 — Market Intelligence
* ADR-014 — Decision pipeline
* ADR-020 — Market Intelligence architecture
* ADR-021 — AI execution governance
* ADR-022 — Intelligence artifacts
* ADR-023 — Capability execution model
* ADR-025 — Observation model
* ADR-026 — Trading Opportunity model
* ADR-027 — Trade Planning
* ADR-028 — Risk Domain

The Repository Analysis must verify the exact current paths, statuses, and implementation boundaries of these ADRs.

---

## Relevant Modules

* `market-intelligence`
* `market-data`

`trading-core` may be inspected only to verify the existing downstream Trade Plan risk contract. It should not be modified unless Repository Analysis proves a minimal contract correction is required.

---

## Validation

At minimum:

* focused tests for completed analysis to observation creation;
* tests proving failed or incomplete results do not create observations;
* observation-to-opportunity tests;
* opportunity deduplication and versioning tests;
* Trading Context assembly tests;
* Trade Plan creation and persistence tests;
* idempotent replay tests;
* traceability tests across the complete intelligence chain;
* complete Market Intelligence Maven test suite;
* Market Data tests only if its contracts are modified;
* `git diff --check`.

Each Maven service must be tested independently.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved when required
* [ ] One authoritative production orchestration path selected
* [ ] Connected production pipeline implemented
* [ ] Relevant automated tests pass
* [ ] No risk evaluation or execution triggered
* [ ] Diff reviewed in IntelliJ
* [ ] Human corrections completed
* [ ] Code Review approved
* [ ] Engineering Report completed
* [ ] Human commit created
