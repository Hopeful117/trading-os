# Story 0113 - Realistic Execution and Transaction-Cost Research Foundation

## Metadata

**ID:** `0113`

**Title:** Realistic Execution and Transaction-Cost Research Foundation

**Status:** Approved

**Related Stories:** `0107A`, `0107C`, `0108A`, `0108B`, `0109B`, `0111`, `0112A`, `0112B`

**Related ADRs:** `ADR-029`, `ADR-030`, `ADR-035`, `ADR-042`, `ADR-051`,
`ADR-056`, `ADR-059`, `ADR-060`, `ADR-061`, `ADR-062`, `ADR-063`, `ADR-064`

This Story is generated from the repository investigation recorded in
`repository-analysis.md`. It was approved by the human engineer for the
documented planning and follow-up Story decomposition. Approval does not
authorize production implementation outside the approved follow-up Stories.

---

## Goal

Establish the durable evidence, temporal contracts, and deterministic economic
result boundary required to evaluate execution realism and transaction costs
without turning Market Intelligence into an execution engine or changing the
meaning of objective market outcomes.

The first implementation scope must prevent false precision. It must preserve
explicit missingness and support reproducible results from historical evidence,
versioned policies, and immutable inputs.

## Context

Trading Core currently owns execution intents, execution attempts, PAPER
settlement, and trade outcomes. Its simulated execution is intentionally
bounded: it can use the current simulated bid/ask but does not model durable
historical liquidity, order-book walking, latency, queue position, partial
fills, funding, or historical fee schedules.

Market Data already provides temporal evidence patterns and is establishing
strict Order Book replay. However, durable funding evidence, historical fee
schedule evidence, and suitable latency evidence are not currently available.

Market Intelligence owns deterministic market interpretation and objective
outcome evaluation. `ObservedMarketOutcome` describes what the market did; it
must not become a strategy-specific P&L or simulated execution result.

## Problem

The platform cannot currently claim realistic historical execution or
transaction-cost results because important inputs are absent or would require
using current provider state. Implementing a broad simulator before acquiring
those inputs would create non-reproducible results, look-ahead risk, hidden
assumptions, and possible double counting of spread, slippage, fees, or
funding.

## Scope

- Define the provider-neutral evidence requirements for historical funding,
  fee schedules, effective intervals, market constraints, and execution
  provenance.
- Define strict temporal eligibility using evaluation cutoff, `occurredAt`,
  `knownAt`, effective intervals, source provenance, and deterministic
  fingerprints.
- Define the boundary between objective market outcomes and strategy-specific
  economic results.
- Define versioned, provider-neutral concepts equivalent to:
  - reference market state;
  - strategy intent;
  - simulated execution;
  - transaction-cost components;
  - economic result.
- Define explicit statuses for unavailable, stale, insufficient, unsupported,
  or degraded evidence.
- Define the single representation of each execution effect so spread crossing,
  slippage, fees, and funding cannot be double-counted.
- Define a narrow first simulation policy, initially limited to market-order
  behavior that is justified by available historical evidence.
- Preserve Java as the authority for deterministic market, execution, Risk,
  and cost calculations.
- Preserve Python as a downstream consumer of validated Java-produced research
  datasets and economic results.
- Define the decomposition of future implementation stories, prioritizing data
  capture that cannot be reconstructed later, especially funding evidence.

## Out of Scope

- A broad backtesting engine or strategy-performance platform.
- Changes to the meaning or schema of `ObservedMarketOutcome`.
- Moving Risk, authorization, execution state, or settlement ownership to
  Market Intelligence.
- Broker calls from Market Intelligence.
- Provider-specific DTOs or Kraken-specific semantics in domain contracts.
- Automatic fallback to current provider state, current fee configuration, or
  current funding values.
- Silent conversion of missing costs into zero, neutral, false, or NaN values.
- Queue-position modeling, latency calibration, sophisticated partial-fill
  behavior, or provider-specific matching simulation. A bounded partial-fill
  representation may preserve observed filled and unfilled quantities without
  claiming matching realism.
- Catalog-wide or multi-provider rollout.
- Python-owned market facts, execution semantics, Risk, or transaction-cost
  calculations.
- Frontend, Gateway, public API, or unrelated service responsibility changes.
- Production implementation outside explicitly approved follow-up Stories.

## Acceptance Criteria

- [ ] The evidence requirements and ownership for funding, fee schedules,
      effective intervals, market state, and execution provenance are
      documented and consistent with the service boundaries.
- [ ] Historical eligibility is defined using an explicit evaluation cutoff,
      `occurredAt`, `knownAt`, effective intervals, provenance, and versioned
      fingerprints.
- [ ] Objective market outcomes are explicitly separated from strategy-specific
      simulated executions, transaction costs, and economic results.
- [ ] The proposed economic-result contract identifies execution model,
      calculation policy, source evidence, version, missingness, and degraded
      status.
- [ ] The representation of spread crossing, slippage, fees, and funding is
      mutually exclusive where necessary and includes tests or invariants that
      prevent double counting.
- [ ] Missing, stale, insufficient, unavailable, and unsupported evidence is
      represented explicitly and is never silently replaced with zero or a
      current-state value.
- [ ] The initial simulation scope is limited to behavior justified by durable
      evidence and does not claim unsupported limit-order, queue, latency, or
      partial-fill realism.
- [ ] Deterministic calculation authority remains in Java; Python consumes
      validated outputs and does not redefine economic semantics.
- [ ] The first implementation sequence prioritizes durable funding and fee
      evidence before broader execution realism.
- [ ] The resulting implementation decomposition identifies the service owner,
      persistence boundary, migration needs, validation surface, and explicit
      deferred work for each follow-up Story.
- [ ] No changes are introduced to Risk authority, `ObservedMarketOutcome`,
      existing immutable feature projection semantics, or unrelated service
      responsibilities.

## Constraints

- Market Data remains authoritative for normalized historical market evidence,
  temporal eligibility, and replay truth.
- Trading Core remains authoritative for execution intents, execution state,
  Risk orchestration, PAPER settlement, and economic-result application.
- Market Intelligence remains responsible for deterministic interpretation and
  must not become a second execution engine.
- Broker Service exposes provider-neutral live execution facts and keeps
  provider-specific behavior inside infrastructure adapters.
- The same typed inputs, policy parameters, and versions must produce the same
  deterministic outputs and fingerprints.
- Current provider state must never be used as a historical fallback.
- Existing repository changes unrelated to this Story must be preserved.
- This Story does not authorize a commit, push, merge, or deployment.

## Relevant Artifacts

- `docs/architecture/stories/0113-realistic-execution-transaction-cost-research/repository-analysis.md`
- `docs/architecture/stories/0113-realistic-execution-transaction-cost-research/implementation-plan.md`
- `docs/architecture/adr/ADR-042.md`
- `docs/architecture/adr/ADR-051.md`
- `docs/architecture/adr/ADR-056.md`
- `docs/architecture/adr/ADR-059.md`
- `docs/architecture/adr/ADR-060.md`
- `docs/architecture/adr/ADR-062.md`
- `docs/architecture/adr/ADR-063.md`
- `docs/architecture/adr/ADR-064.md`

## Validation Expectations

- Review the repository analysis and this Story with the human engineer.
- Approve the implementation decomposition before production implementation.
- Add deterministic tests for cost components, missingness, temporal
  eligibility, provenance, policy versioning, and fingerprints.
- Add tests proving that spread, slippage, fee, and funding effects are not
  double-counted.
- Add contract tests proving separation between objective outcomes and economic
  results.
- Validate that current provider state and current configuration are never used
  for historical replay.
- Run the relevant Java service verification and `git diff --check` for each
  implementation Story.
- Validate Python consumption only after Java has produced and validated the
  authoritative dataset or economic-result contract.

## Definition of Done

- [x] Human approval recorded for this Story.
- [ ] Follow-up implementation Stories are identified and scoped.
- [ ] Required ADR changes, if any, are identified before implementation.
- [ ] Evidence and economic-result contracts are implemented only through
       explicitly approved follow-up Stories. The current worktree changes
       require that follow-up authorization to be recorded before integration.
- [ ] Relevant validation passes for each follow-up implementation.
- [ ] Code review and engineering reporting are completed.
- [ ] Human commit and integration decisions are completed.
