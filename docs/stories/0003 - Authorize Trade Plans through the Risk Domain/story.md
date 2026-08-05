# Story

## Metadata

**ID:** `0003`

**Title:** Authorize Trade Plans through the Risk Domain

**Status:** Draft

---

## Goal

Complete the Trading Intelligence pipeline by evaluating immutable Trade Plans
through the deterministic Risk Domain and producing an immutable authorization
decision before any broker execution is allowed.

---

## Context

Story 0002 completed the Trade Planning pipeline and introduced the
TradePlanningContext defined by ADR-031.

Market Intelligence is now capable of producing immutable Trade Plans from
market opportunities.

The next step is to evaluate these Trade Plans using the deterministic Risk
Domain described by ADR-028.

Trading Core must assemble the authoritative RiskEvaluationContext from the
current account, portfolio, market and rule snapshots before invoking the
Risk Domain.

No broker execution is part of this Story.

---

## Problem

Trade Plans can now be generated but cannot yet be evaluated through the
production Risk Domain.

The platform currently lacks the deterministic authorization step separating
planning from execution.

Without this step, Trading OS cannot determine whether a Trade Plan is
financially acceptable before reaching the Broker Service.

---

## Scope

- Assemble RiskEvaluationContext inside Trading Core.
- Resolve authoritative snapshots required by ADR-028.
- Invoke the Risk Domain.
- Produce immutable RiskEvaluation results.
- Persist authorization decisions.
- Implement idempotent evaluation.
- Expose an authorization REST endpoint.
- Add targeted tests for the authorization flow.

---

## Out of Scope

- Broker execution.
- Order placement.
- Kraken integration changes.
- Position monitoring.
- Market Intelligence analysis.
- AI recommendations.
- Frontend execution workflow.

---

## Acceptance Criteria

- [ ] Trade Plans can be evaluated through the Risk Domain.
- [ ] Trading Core assembles RiskEvaluationContext from authoritative snapshots.
- [ ] Risk evaluations are immutable and persisted.
- [ ] Authorization decisions are deterministic.
- [ ] Authorization supports AUTHORIZED, REJECTED and UNKNOWN outcomes.
- [ ] Evaluation is idempotent.
- [ ] Traceability from Trade Plan to RiskEvaluation is preserved.
- [ ] Relevant tests pass.
- [ ] No unrelated behavior is changed.

---

## Constraints

- Preserve existing service responsibilities.
- Respect ADR-028 and ADR-031.
- Keep business and risk decisions deterministic.
- Trading Core owns RiskEvaluationContext assembly.
- Risk Domain never accesses Broker Service directly.
- Broker Service remains a provider of facts only.
- Avoid provider-specific leakage outside infrastructure adapters.
- Do not introduce unrelated dependencies.
- Do not commit, push, or merge automatically.

---

## Relevant ADRs

- `ADR-028 — Deterministic Risk Domain`
- `ADR-029 — Trade Execution Lifecycle`
- `ADR-031 — Clarify Trade Planning Context and Risk Context Responsibilities`

---

## Relevant Modules

- `trading-core`
- `risk-domain`

---

## Validation

Expected validation:

- targeted Maven tests for `trading-core`;
- targeted Maven tests for `risk-domain`;
- end-to-end authorization flow validation;
- architecture validation against ADR-028;
- manual review in IntelliJ;
- verification that no broker execution occurs.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.