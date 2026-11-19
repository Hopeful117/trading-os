# Story 0247-02 - Opportunity Outcome Capture

## Status

Draft - derived from Epic 0247 and pending Repository Analysis approval.

## Identity

- **ID:** `0247-02`
- **Epic:** `0247 - Trading Intelligence Evidence and Opportunity Quality V1`
- **Predecessor:** Story `0247-01 - Deterministic Evidence Quality Contract`
- **Primary module:** `market-intelligence`

## Goal

Record deterministic, immutable observations of what happens after a trading opportunity is
detected, without presenting market movement as executed-trade performance or creating trading
authority.

## Context

Trading OS already persists objective future-market facts as `ObservedMarketOutcome`. That contract
is intentionally market-scoped and must remain separate from strategy, opportunity, execution, risk,
and P&L semantics. Opportunity lifecycle state currently distinguishes detection, analysis,
activation, consumption, and expiration, while passive evaluation can diagnose supersession and
invalidation conditions without persisting an opportunity outcome fact.

This Story defines the missing opportunity-level observation boundary. It must reuse existing
temporal and immutable-evidence rules rather than reinterpret `ObservedMarketOutcome` as a trade
result.

## Problem

The system cannot currently answer, in a deterministic and auditable way, what happened to a
detected opportunity under a defined evaluation cutoff and horizon. Expiration, invalidation,
supersession, subsequent market movement, incomplete evidence, and unknown outcomes are not exposed
as one versioned opportunity-outcome contract.

## Scope

### In scope

- A provider-neutral, immutable opportunity-outcome contract.
- Distinct outcome states for expiration, invalidation, supersession, subsequent market movement,
  unknown outcome, and non-evaluable evidence.
- Persisted evaluation cutoff and evaluation horizon.
- Provenance linking the outcome to the exact opportunity version and source evidence.
- Deterministic identity and duplicate-safe persistence.
- Reuse of existing historical market evidence and objective market-outcome services where their
  semantics match.
- Tests for fixed temporal fixtures, cutoff and horizon boundaries, duplicate evaluation,
  incomplete evidence, and look-ahead prevention.

### Out of scope

- Trade performance, P&L, win rate, expectancy, probability, or profitability claims.
- Broker, execution, risk, settlement, or position-management changes.
- Automatic trading or ActiveScan creation.
- AI-generated outcome interpretation.
- Global historical backfill.
- New microservices or generic workflow infrastructure.
- Trader-facing Angular changes; those belong to Story `0247-05` unless a concrete contract gap is
  demonstrated.

## Acceptance Criteria

- Expiration, invalidation, supersession, subsequent market movement, and unknown outcomes are
  distinguishable in the persisted contract.
- Evaluation horizon and source cutoff are persisted and participate in deterministic identity where
  semantically required.
- Market outcomes remain separate from executed-trade outcomes and contain no execution, risk,
  commission, funding, settlement, or P&L semantics.
- Repeating an evaluation with the same semantic inputs is idempotent and does not create a second
  outcome fact.
- Incomplete, unavailable, stale, or otherwise inadmissible evidence produces an explicit
  non-evaluable result rather than an inferred positive or negative result.
- Future evidence cannot enter the opportunity context or change the original opportunity evidence
  fingerprint.
- Corrections or changed outcome definitions create a new immutable version/fingerprint rather than
  mutating an existing fact.

## Constraints

- `Market Data` remains authoritative for provider-neutral observed market facts.
- `Market Intelligence` owns deterministic opportunity interpretation and outcome capture.
- `Trading Core` and `Risk Domain` remain authoritative for account, risk, authorization, and
  execution state.
- The human trader remains the final authority for any trading decision.
- Existing `ObservedMarketOutcome` semantics from ADR-059/ADR-060/ADR-061 must not be broadened
  implicitly.
- Known-at, evaluation-cutoff, maturity, provenance, fingerprint, and fail-closed semantics remain
  authoritative.

## Relevant Artifacts

- `docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md`
- `docs/architecture/adr/ADR-059.md`
- `docs/architecture/adr/ADR-060.md`
- `docs/architecture/adr/ADR-061.md`
- `docs/architecture/stories/0111-continuous-research-data-lifecycle/story.md`
- `docs/architecture/stories/0213-controlled-historical-ohlc-reconstruction/story.md`

## Validation Expectations

- Domain tests for every outcome state and invariant.
- Fixed-clock tests at cutoff, before maturity, and after maturity.
- Idempotency and concurrent duplicate tests.
- Fingerprint and immutable-correction tests.
- Incomplete, unavailable, stale, post-cutoff, and look-ahead fixtures.
- Persistence round-trip tests.
- Targeted `market-intelligence` verification and `git diff --check`.
