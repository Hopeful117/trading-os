# Story 0079 - PAPER Challenge Progression and Risk Monitoring

## Metadata

**ID:** `0079`
**Title:** PAPER Challenge Progression and Risk Monitoring
**Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
**Related ADRs:** `ADR-009`, `ADR-028`, `ADR-029`, `ADR-031`

---

## Goal

Connect the provider-neutral Challenge foundation from Story 0078 to
authoritative PAPER Account state and the deterministic Risk Domain so an
ACTIVE Challenge can progress to PASSED or BREACHED after reliable PAPER state
changes.

## Context

Story 0078 introduced immutable versioned Challenge definitions and optimistic,
terminal Challenge instances owned by Trading Core. Story 0077 introduced
provider-neutral daily and total drawdown semantics with immutable Risk
baselines and replayable Risk context.

The first operational loop must reuse those capabilities. Account monitoring
evaluates observed Account facts only; it does not construct a dummy TradePlan,
duplicate Risk formulas, or introduce provider-specific behavior.

## Problem

An ACTIVE Challenge currently has no runtime evaluator, no account-monitoring
Risk orchestration, no derived progress projection, and no post-settlement hook.
PAPER settlement updates Account state, but that authoritative state is not yet
connected to Challenge lifecycle transitions.

## Scope

- Add the smallest application-level account-monitoring Risk capability using
  `ACCOUNT_MONITORING` and the existing deterministic Risk engine.
- Persist account-monitoring Risk evaluations using the generic Risk audit model
  with explicit mode and provenance.
- Add a deterministic Challenge evaluator with safety-first BREACHED-before-PASSED
  ordering and terminal idempotency.
- Evaluate BALANCE progression inclusively against the exact definition target.
- Reevaluate an optional ACTIVE Challenge after successful PAPER settlement using
  post-mutation Account state.
- Add a derived `ChallengeProgress` read model without continuously persisting
  changing projection values.
- Expose only a minimal ownership-checked Challenge read API if existing
  Trading Core conventions support it without broad CRUD scope.
- Add focused Risk, evaluator, persistence/provenance, and PAPER integration
  coverage.

## Out of Scope

- Continuous mark-to-market monitoring when persisted PAPER equity is not
  authoritative.
- Full dynamic multi-asset valuation or websocket-driven monitoring.
- Kraken Prop synchronization, trading, payouts, KYC, funded lifecycle, or
  commercial plan configuration.
- Trailing drawdown, multi-stage workflows, reset, multiple active Challenges,
  or same-Account restart.
- Frontend, new services, Kafka, Redis, historical simulation, Quant, ML, or AI.

## Acceptance Criteria

- [ ] Account monitoring evaluates observed PAPER state with no TradePlan.
- [ ] Account monitoring uses the effective Challenge Risk Policy/version and
      persists reproducible mode/provenance as `ACCOUNT_MONITORING`.
- [ ] Daily and total drawdown remain solely owned by the Risk Domain.
- [ ] Exact daily and total thresholds use existing Risk semantics.
- [ ] `ACTIVE` with no terminal breach and target not met remains `ACTIVE`.
- [ ] Inclusive exact target and exceeded BALANCE target transition to `PASSED`.
- [ ] A blocking Challenge-terminal Risk violation transitions to `BREACHED`.
- [ ] If target and terminal breach coincide, Risk breach wins deterministically.
- [ ] Non-terminal warnings do not breach a Challenge.
- [ ] Terminal reevaluation is harmless and cannot change terminal state.
- [ ] PAPER settlement reevaluates after authoritative Account mutation.
- [ ] Accounts without a Challenge settle exactly as before.
- [ ] Concurrent terminal reevaluation cannot overwrite a terminal state.
- [ ] `ChallengeProgress` contains only correctly derived available values.
- [ ] Open-position continuous monitoring is explicitly not claimed where PAPER
      equity is not authoritative.
- [ ] No provider-specific business branch or duplicated Risk formula exists.

## Constraints

- Preserve ADR-009 provider neutrality and ADR-028 deterministic Risk ownership.
- Preserve Story 0078 lifecycle methods, optimistic locking, and one-ACTIVE
  invariant.
- Keep `Account` authoritative for PAPER financial state and `BrokerAccount`
  technical.
- Do not introduce a parallel `ChallengeRiskEvaluation` audit mechanism.
- Do not silently substitute equity for the BALANCE progression source.
- Do not make Challenge participation mandatory for PAPER execution.

## Relevant Modules

- `trading-core`
- `risk-domain`
- `docs/architecture/stories/0077-kraken-compatible-generic-risk-semantics`
- `docs/architecture/stories/0078-challenge-definition-instance-domain`

## Validation

- Focused Risk account-monitoring tests.
- Focused Challenge evaluator and projection tests.
- PAPER settlement integration tests for ACTIVE, PASSED, BREACHED, no Challenge,
  and repeated evaluation.
- Complete Trading Core `mvn verify`.
- Complete Risk Domain test suite if modified.
- `git diff --check` and complete diff inspection.

## Definition of Done

- [x] Implementation completed.
- [x] Story implementation plan and report completed.
- [x] Relevant validation executed and reported exactly.
- [x] Continuous mark-to-market limitation documented.
- [x] Complete diff reviewed for provider leakage, duplicated Risk logic, stale
      state, and accidental mandatory Challenge behavior.
- [ ] Human review and commit completed.
