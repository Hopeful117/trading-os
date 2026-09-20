# Story 0059 - Evaluate Manual Trade Plans through Deterministic Risk

## Metadata

**ID:** `0059`
**Title:** Evaluate Manual Trade Plans through Deterministic Risk
**Status:** Draft

---

## Goal

Allow a manual-origin Trade Plan created by Story 0058 to pass through the same
deterministic Risk Evaluation pipeline as an opportunity-origin Trade Plan.

The manual origin must not create a special authorization path:

```text
MANUAL TradePlan
    -> Trading Core authoritative context
    -> Risk Domain
    -> immutable RiskEvaluation
    -> human review
```

No broker execution or Execution Intent creation is included in this Story.

---

## Context

Story 0058 introduced `TradePlanOrigin.MANUAL` and an authenticated manual Trade
Plan creation contract. The resulting plan is persisted as `PROPOSED` and carries
explicit instrument, direction, execution and sizing inputs.

Trading Core already owns the Risk Evaluation entry point and assembles the
authoritative account, market, portfolio and rule context. The Risk Domain is
deterministic and must remain the authority for financial acceptance.

The existing risk pipeline was designed around Trade Plans and must consume the
manual origin without requiring a Trading Opportunity or introducing a second
risk contract.

---

## Problem

The manual Trade Plan can currently be created, but the complete path from
manual proposal to deterministic risk verdict has not been demonstrated.

The implementation must prove that:

* manual plans are accepted by the existing risk handoff;
* account ownership and context checks remain authoritative;
* unsupported, stale or incompatible facts fail closed;
* Opportunity provenance is not required for risk evaluation;
* no Execution Intent or broker mutation occurs as a side effect.

---

## Scope

### Included

* Consume `MANUAL` Trade Plans through the existing Trading Core risk flow.
* Preserve authoritative account ownership validation.
* Preserve current planning-context and version checks.
* Assemble Risk Evaluation Context from current authoritative snapshots.
* Invoke the existing Risk Domain without changing rule semantics.
* Persist and expose the immutable Risk Evaluation result.
* Preserve `AUTHORIZED`, `REJECTED` and `UNKNOWN` semantics.
* Preserve idempotency and traceability from manual Trade Plan to RiskEvaluation.
* Add regression tests for approved, rejected, unauthorized and unavailable-fact
  manual plans.
* Add compatibility coverage proving opportunity-origin plans remain unchanged.

### Manual-specific behavior

* Empty Opportunity and Observation provenance is valid for `MANUAL` plans.
* Deterministic risk facts, not the manual rationale, decide authorization.
* A rejected or unknown result must not create an Execution Intent.
* An authorized result remains subject to explicit human validation and the
  existing execution guards.

---

## Out of Scope

* Creating or modifying manual Trade Plans.
* Manual-trade frontend form or risk-result redesign.
* Changing Risk Profile thresholds or rule semantics.
* New position-sizing algorithms.
* Automatic risk approval.
* Execution Intent creation.
* Broker Service calls or order placement.
* PAPER settlement or LIVE execution.
* Partial close, SL/TP modification or position management.
* New broker capabilities or provider integrations.
* Replacing the existing Risk Evaluation Context authority.

---

## Acceptance Criteria

* [ ] A valid `MANUAL` Trade Plan reaches the existing Trading Core Risk
      Evaluation flow without requiring an Opportunity.
* [ ] Trading Core assembles the Risk Evaluation Context from authoritative
      account, portfolio, market and rule snapshots.
* [ ] Manual Trade Plans use the same Risk Domain rules and thresholds as
      opportunity-origin Trade Plans.
* [ ] Authorized, rejected and unknown risk outcomes remain explicit and
      immutable.
* [ ] A rejected or unknown manual risk result cannot create an Execution Intent.
* [ ] Account ownership is derived from the authenticated principal and cannot
      be overridden by manual request data.
* [ ] Stale, missing or unavailable required facts fail closed according to the
      existing risk contract.
* [ ] Risk Evaluation idempotency is preserved for repeated evaluation requests.
* [ ] Traceability from manual Trade Plan origin and version to RiskEvaluation is
      preserved.
* [ ] Existing opportunity-origin Trade Plan risk evaluation remains green.
* [ ] Focused Trading Core and Risk Domain tests pass.
* [ ] `git diff --check` passes.

---

## Constraints

* ADR-028 remains authoritative for deterministic risk decisions.
* ADR-029 remains authoritative for the boundary before execution.
* ADR-031 remains authoritative for planning versus financial context.
* ADR-044 remains authoritative for authenticated actor and ownership checks.
* ADR-047 remains authoritative for manual Trade Plan provenance.
* Risk Domain must not access Broker Service directly.
* Trading Core remains responsible for assembling Risk Evaluation Context.
* Existing `ExecutionIntent` creation guards must remain unchanged.
* No risk threshold may be weakened to make a manual scenario pass.
* No client-provided actor, account balance, margin or market fact is authoritative.

---

## Architectural Decision

`NEW_ADR_REQUIRED = NO`.

This Story composes the existing Trade Plan and Risk Evaluation contracts. A new
ADR is required only if implementation discovers that manual plans need a new
Risk Domain authority, a different financial context, or a second authorization
pipeline.

---

## Relevant ADRs

* `ADR-001` - Trading OS Vision and Human Authority
* `ADR-014` - Trading Decision Pipeline
* `ADR-028` - Deterministic Risk Domain
* `ADR-029` - Execution Domain Architecture
* `ADR-031` - Trade Planning Context and Risk Context Responsibilities
* `ADR-044` - Inter-Service Trust and Actor Propagation
* `ADR-047` - Manual Trade Plans as a First-Class Trade Plan Origin

---

## Related Stories

* `0003` - Authorize Trade Plans Through Risk Domain
* `0023` - Opportunity Trade Plan Risk Decision
* `0034` - Execution-Time Risk Revalidation
* `0051` - Broker Capabilities and Margin Facts
* `0055` - Make PAPER Trade Plan Sizing Compatible with Effective Risk
* `0058` - Create Manual-Origin Trade Plans

---

## Relevant Modules

* `trading-core`
* `risk-domain`
* `market-intelligence` only where the manual risk snapshot contract requires
  compatibility changes

---

## Validation

* Trading Core manual Trade Plan risk evaluation tests.
* Risk Domain deterministic rule tests using manual-plan inputs.
* Unauthorized account and cross-account negative tests.
* Missing, stale and unavailable-fact fail-closed tests.
* Idempotency and immutable RiskEvaluation persistence tests.
* Regression tests for opportunity-origin Trade Plans.
* `git diff --check`.

Runtime validation should demonstrate a manual plan reaching an explicit risk
verdict without creating an Execution Intent or broker mutation.

---

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved when required.
* [ ] Story scope approved.
* [ ] Implementation completed within this Story's scope.
* [ ] Acceptance criteria validated with evidence.
* [ ] Human code review completed.
* [ ] Human commit created.
