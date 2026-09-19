# Story 0046 - Make Trade Plan frontend state and errors reliable

## Metadata

**ID:** `0046`

**Title:** Make Trade Plan frontend state and errors reliable

**Status:** Review

---

## Goal

An authenticated trader can create, review, accept or reject, evaluate, and
authorize a Trade Plan without the interface becoming stuck, submitting the
same command twice, or hiding an actionable backend result.

The frontend must reflect the authoritative Trade Plan and risk states without
reimplementing lifecycle, sizing, or risk rules.

---

## Context

The opportunity-to-risk flow from Story 0023 exists in Angular. The frontend
also contains execution actions from Stories 0030, 0031, and 0034.

The current implementation exposes `busy$` but does not use it to disable
actions. Creation errors are swallowed after the view enters `creating`, and
several persisted Trade Plan states are rendered as `ACCEPTED` by default.

The current frontend baseline is green, but its tests do not cover the
critical retry, duplicate-action, reload, and conflict scenarios.

---

## Problem

The trader can lose the ability to continue after a failed plan creation. A
double click can issue repeated commands. A page reload can present a plan in
an incorrect action state. Generic error messages do not explain expiration,
version conflicts, ownership failures, or risk refusals.

These behaviors reduce trust in the human-validation boundary before
execution.

---

## Scope

* Model the Trade Plan page as explicit observable UI states for loading,
  proposal, decision in progress, accepted, risk evaluation in progress,
  risk decision, execution-ready, execution in progress, and terminal states.
* Disable or otherwise guard actions while a command is in flight.
* Prevent duplicate create, decision, risk-evaluation, and execution commands
  from the same user interaction.
* Preserve the last useful view when a transient request fails.
* Render actionable and safe messages for not found, expired, conflict,
  forbidden, validation, and unavailable responses.
* Provide a retry path for recoverable plan-loading or plan-creation failures.
* Handle `PROPOSED`, `ACCEPTED`, `REJECTED`, `RISK_VALIDATED`,
  `READY_TO_EXECUTE`, `EXECUTED`, and `EXPIRED` explicitly when the backend
  returns them.
* Add focused Angular tests for transitions, duplicate actions, reloads,
  failures, retries, and backend error mapping.

---

## Out of Scope

* Changes to Trade Plan lifecycle rules, risk calculations, or execution
  authorization.
* Automatic acceptance, automatic risk evaluation, or automatic execution.
* A new state manager or UI framework.
* Advanced order-ticket editing, re-planning, or plan history browsing.
* Changes to broker integrations.
* Bundle-budget remediation unrelated to touched screens.

---

## Acceptance Criteria

* [x] A failed Trade Plan creation leaves the trader on an actionable error
      state and allows an explicit retry.
* [x] Accept, reject, risk evaluation, and execute controls cannot submit a
      duplicate command while the corresponding request is in flight.
* [x] Reloading a plan renders the correct available action for every persisted
      lifecycle state listed in Scope.
* [x] Expired, forbidden, not-found, conflict, invalid, and unavailable
      responses are distinguishable to the trader without exposing stack traces.
* [x] A rejected risk decision never exposes an execution action.
* [x] A recoverable error preserves enough context for the trader to continue
      or return safely to the opportunity.
* [x] Angular tests cover the state transitions, duplicate-action protection,
      reload behavior, error mapping, and retry behavior.
* [x] `npm run test:ci`, `npm run build`, and `git diff --check` pass.
* [x] No unrelated behavior is changed.

---

## Constraints

* Trading Core, Market Intelligence, and Risk Domain remain authoritative for
  lifecycle and risk decisions.
* The frontend must not compute sizing, risk, expiration, or authorization.
* Preserve explicit human actions before risk evaluation and execution.
* Follow the existing standalone Angular, Observable, and async-pipe patterns.
* Preserve idempotency headers and backend ownership checks.
* Do not expose provider-specific broker payloads.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-027.md` - Trade Planning Model
* `docs/architecture/adr/ADR-028.md` - Deterministic Risk
* `docs/architecture/adr/ADR-029.md` - Execution Domain Architecture

---

## Relevant Modules

* `trading-os-web`
* `trading-core` - only if an existing public error contract must be clarified

---

## Validation

* Focused Angular component and service tests.
* Angular production build.
* Manual verification of the authenticated opportunity-to-plan flow.
* Verification that risk and execution actions remain human-triggered.
* Review of the public error responses used by the frontend when required.

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
