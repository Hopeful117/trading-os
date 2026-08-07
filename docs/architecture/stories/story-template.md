# Story

## Metadata

**ID:** `0000`

**Title:** Short action-oriented title

**Status:** Draft

Possible statuses:

* Draft
* Approved
* In Progress
* Review
* Completed
* Blocked

---

## Goal

Describe the product or engineering outcome expected from this Story.

---

## Context

Provide the minimum context required to understand the work.

Reference existing implementations and accepted ADRs rather than repeating them.

---

## Problem

Describe the current limitation or missing behavior.

Avoid prescribing implementation details unless they are mandatory constraints.

---

## Scope

List the behavior and modules included in the Story.

---

## Out of Scope

List explicitly excluded changes.

---

## Acceptance Criteria

* [ ] First observable outcome
* [ ] Second observable outcome
* [ ] Relevant tests pass
* [ ] No unrelated behavior is changed

---

## Constraints

* Preserve existing service responsibilities.
* Respect accepted ADRs.
* Keep business and risk decisions deterministic.
* Avoid provider-specific leakage outside infrastructure adapters.
* Do not introduce unrelated dependencies.
* Do not commit, push, or merge automatically.

---

## Relevant ADRs

* `docs/adr/...`

Use `None` when no ADR is directly relevant.

---

## Relevant Modules

* `trading-core`
* `broker-service`
* `market-data`
* `market-intelligence`
* `risk-domain`
* `gateway`
* `trading-os-web`

Keep only the modules relevant to the Story.

---

## Validation

Describe the expected validation:

* targeted Maven tests;
* Angular tests;
* production build;
* architecture tests;
* manual verification;
* sandbox or E2E validation.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved when required
* [ ] Implementation completed
* [ ] Relevant validation executed
* [ ] Diff reviewed in IntelliJ
* [ ] Code Review approved
* [ ] Engineering Report completed
* [ ] Human commit created
