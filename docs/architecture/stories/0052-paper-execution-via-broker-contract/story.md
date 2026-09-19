# Story 0052 - Align execution contracts without changing PAPER authority

## Metadata

**ID:** `0052`
**Title:** Align execution contracts while preserving local PAPER settlement
**Status:** Draft

---

## Goal

Give PAPER and LIVE compatible execution concepts and technical contracts while
preserving the ADR-042 rule that Trading Core owns PAPER position truth and
local settlement.

PAPER must not be routed through LIVE Broker Service position mutation or
external reconciliation.

## Context

ADR-029 already defines ExecutionIntent, ExecutionAttempt, idempotency,
unknown outcomes, reconciliation, and Broker Service as the technical adapter.
ADR-042 explicitly keeps PAPER mutation and settlement local to Trading Core.

This Story therefore aligns shared execution concepts without moving PAPER
position authority to Broker Service.

## Scope

* Preserve or complete broker-neutral execution lifecycle states.
* Bind execution to exact Trade Plan, risk evaluation, account, and capability
  versions.
* Revalidate risk and technical facts before execution.
* Keep PAPER submission and settlement local to Trading Core.
* Keep LIVE submission behind Broker Service.
* Represent ambiguous LIVE outcomes as `UNKNOWN` and reconcile them.
* Represent PAPER local transaction failure as rollback or explicit recovery,
  not as fake provider reconciliation.
* Support explicit full PAPER exit through the existing mode-aware product
  boundary.
* Preserve idempotency and human authorization.
* Extend frontend state only where required to present neutral execution states.

## Out of Scope

* Making Broker Service the PAPER position authority.
* Calling Broker Service for PAPER mutation or close.
* Partial close or exposure reversal.
* Automatic exits, trailing stops, or SL/TP modification.
* Event sourcing or immediate WebSocket/SSE adoption.
* Changing LIVE position authority.

## Acceptance Criteria

* [ ] PAPER execution remains local and reloadable.
* [ ] LIVE execution remains Broker Service-backed.
* [ ] Both modes use compatible neutral lifecycle concepts.
* [ ] Execution references exact plan, risk, account, and capability versions.
* [ ] Full risk revalidation occurs before execution.
* [ ] Capability changes block execution and require revalidation.
* [ ] Duplicate requests cannot create duplicate logical executions.
* [ ] Ambiguous LIVE outcomes become `UNKNOWN` and require reconciliation.
* [ ] PAPER local transaction failure cannot report false success.
* [ ] Full PAPER exit remains human-authorized and local.
* [ ] Fills and reconciliation outcomes are auditable.
* [ ] Affected backend and frontend tests pass.
* [ ] `git diff --check` passes.

## Related ADRs

* `ADR-028.md`
* `ADR-029.md`
* `ADR-030.md`
* `ADR-042.md`
* `ADR-044.md`
* `ADR-045.md`

## Related Stories

* `0030-connect-risk-decision-to-human-controlled-execution`
* `0031-close-the-execution-feedback-loop`
* `0038-persisted-paper-execution-regression`
* `0042-paper-local-full-exit`
* `0048-paper-trading-journey-runtime-acceptance`

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved.
* [ ] Mode-specific execution behavior implemented.
* [ ] Affected tests pass.
* [ ] Human code review completed.
* [ ] Engineering Report completed.
* [ ] Human commit created.
