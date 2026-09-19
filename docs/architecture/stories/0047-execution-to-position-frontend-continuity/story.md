# Story 0047 - Connect execution feedback to position monitoring

## Metadata

**ID:** `0047`

**Title:** Connect execution feedback to position monitoring

**Status:** Review

---

## Goal

After explicitly authorizing a trade, the trader can understand what happened
and continue directly to the resulting position or its safe terminal outcome.

The experience must preserve the distinction between broker acknowledgement,
fill, failure, and uncertain outcome.

---

## Context

Story 0031 added enriched execution feedback and safe handling of `FAILED`,
`SUBMISSION_OUTCOME_UNKNOWN`, and `RECOVERY_BLOCKED`. Story 0032 added the
dedicated `/positions` page. Stories 0041 and 0042 added PAPER position
valuation and local full exit.

The current Plan page displays execution details but only links back to the
opportunities list. It does not provide continuity to the selected account or
position monitoring. The execution DTO also does not expose all the business
context needed for a concise trade result summary.

---

## Problem

The trader must manually infer the next step after execution. A successful
PAPER execution does not lead naturally to the new position, and a close
operation is not immediately reflected in the position view. Technical
execution identifiers are visible, but instrument, direction, quantity, and
account context are not consistently presented at the point where the trader
needs them.

The frontend must not imply that `COMPLETED` means fully filled when the broker
state says otherwise.

---

## Scope

* Add a clear execution outcome summary containing available instrument,
  direction, quantity, account, execution status, broker status, and fill
  information.
* Add explicit navigation from a successful or terminal execution result to
  the relevant account positions.
* Preserve safe actions for failed and uncertain outcomes: retry only when the
  backend permits it, and reconcile before any retry for uncertain outcomes.
* Refresh or re-enter the selected account context when navigating to
  positions.
* Refresh the positions view promptly after a successful PAPER close command
  and present the close result without waiting for a stale polling cycle.
* Display a truthful degraded state when position data is unavailable rather
  than presenting an unavailable account as an empty account.
* Add focused Angular tests for execution outcomes, navigation, close refresh,
  and uncertain-result safety.
* Identify any missing backend contract required by the frontend and document
  it for separate approval instead of silently changing semantics.

---

## Out of Scope

* Automatic execution, automatic retry, or automatic reconciliation.
* Changes to broker order semantics or execution lifecycle transitions.
* Partial close, stop-loss modification, take-profit modification, or position
  management beyond the existing full-exposure close behavior.
* WebSocket, SSE, or a new real-time transport.
* New PnL or risk calculations in Angular.
* A complete execution history page.

---

## Acceptance Criteria

* [ ] A completed execution result clearly identifies the trade context using
      backend-authoritative data and does not equate broker acceptance with a
      full fill.
* [ ] The execution result provides a direct path to the relevant account
      positions when position monitoring is applicable.
* [ ] `FAILED`, `SUBMISSION_OUTCOME_UNKNOWN`, `RECOVERY_BLOCKED`, and risk
      revalidation outcomes retain their distinct safe actions and messages.
* [ ] An uncertain result never displays a blind retry action.
* [ ] A successful PAPER close updates the visible position state promptly and
      keeps the close result available to the trader.
* [ ] Position retrieval errors are not rendered as a successful empty state
      when the API provides an unavailable or degraded result.
* [ ] Angular tests cover terminal outcomes, navigation, close refresh, and
      safety restrictions.
* [ ] `npm run test:ci`, `npm run build`, and `git diff --check` pass.
* [ ] No unrelated behavior is changed.

---

## Constraints

* Trading Core remains authoritative for execution, account ownership,
  position projection, and risk.
* The frontend must not infer fills, PnL, position identity, or broker state.
* Preserve the distinction between `UNKNOWN` and `FAILED`.
* Preserve explicit human authorization and existing idempotency behavior.
* Angular must call the Gateway and never Broker Service directly.
* Any backend contract change requires explicit scope approval and appropriate
  backend tests.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-029.md` - Execution Domain Architecture

---

## Relevant Modules

* `trading-os-web`
* `trading-core` - only for an explicitly approved contract correction
* `gateway` - only if an intended public route is not reachable

---

## Validation

* Focused Angular execution and positions tests.
* Angular production build.
* Authenticated manual validation with a PAPER account.
* Manual verification of execution result to positions navigation.
* Manual verification of successful and uncertain close outcomes.
* Backend contract tests if a missing response field or degraded-state contract
  is confirmed.

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
