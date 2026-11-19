# Story 0247-03 - Replayable Deterministic Signal Evaluation

## Status

Implementation authorized after Repository Analysis approval.

## Identity

- **ID:** `0247-03`
- **Epic:** `0247 - Trading Intelligence Evidence and Opportunity Quality V1`
- **Predecessor:** Story `0247-02 - Opportunity Outcome Capture`
- **Primary module:** `market-intelligence`
- **Priority:** P1 - Trading Intelligence

## Goal

Provide a bounded, reproducible evaluation of a deterministic signal over strictly admissible
historical evidence, without introducing a generic backtesting framework or making probability,
profitability, risk, or execution claims.

## Context

Trading OS already defines strict historical evidence and known-at semantics through ADR-057 and
ADR-058. Market Intelligence can consume bounded `STRICT_OBSERVED` evidence and run deterministic
analysis capabilities using an explicit evaluation cutoff. Stories `0247-01` and `0247-02` establish
versioned evidence-quality and opportunity-outcome contracts, but the repository still lacks a
focused signal-evaluation artifact that records how a deterministic signal behaved under a named,
reproducible historical context.

Existing historical reconstruction and research lifecycle capabilities must be reused where their
semantics match. This Story must not create a parallel replay pipeline or reinterpret a market
outcome as a trade result.

## Problem

The system cannot currently answer, in a bounded and auditable way, whether the same deterministic
signal evaluation can be reproduced from the same historical evidence selection and policy version.
Without an explicit replay result, post-hoc comparison risks mixing current provider responses,
future evidence, incomplete samples, undocumented cost assumptions, and non-versioned strategy
definitions.

## Scope

### In scope

- A bounded provider-neutral replay/evaluation contract for one deterministic signal definition.
- Explicit replay identity containing the market, product/instrument, timeframe, cutoff, evidence
  policy, strategy/signal version, and selected evidence provenance.
- Strict enforcement of `knownAt <= evaluationCutoff` and the existing historical evidence policy.
- Reuse of the existing deterministic Market Intelligence analysis path.
- Explicit representation of missing, excluded, stale, contradictory, gapped, or incomplete samples.
- Versioned and persisted spread and transaction-cost assumptions as evaluation inputs.
- Deterministic result fingerprinting and duplicate-safe persistence.
- Immutable correction semantics: changed evidence or signal definitions create a new result identity.
- Tests for golden replay results, look-ahead rejection, incomplete history, provenance, and
  fingerprint stability.

### Initial bounded population

- One market per replay request.
- One product/instrument and one timeframe per replay request.
- One evaluation cutoff per replay request.
- One deterministic signal/strategy definition and version per replay request.
- No automatic scheduling, bulk backfill, or unbounded population.

### Out of scope

- Generic backtesting or multi-market research infrastructure.
- Win rate, probability, expectancy, profitability, or calibrated statistical claims.
- Trade simulation, order fills, P&L, risk, settlement, broker, or execution behavior.
- New market-data providers, historical collection, or global backfill.
- New microservices or workflow engines.
- ML training, Python dataset construction, or model calibration.
- Public/Gateway/frontend exposure or automatic ActiveScan creation.
- Mutation of `ObservedMarketOutcome` semantics or executed-trade attribution.

## Acceptance Criteria

- Identical replay inputs, evidence selection, policy versions, and signal definition produce an
  identical deterministic result fingerprint.
- Evidence arriving after the evaluation cutoff, or evidence with `knownAt` after the cutoff, cannot
  enter the replay context or result.
- Strict replay never falls back to a current provider response.
- Provider, product/instrument, timeframe, evidence policy, signal definition/version, cutoff, and
  selected evidence provenance are retained in the result.
- Missing, excluded, stale, contradictory, gapped, and insufficient evidence are explicit and cannot
  be interpreted as a positive signal result.
- Spread and transaction-cost assumptions are explicit, versioned, persisted, and excluded from any
  claim of executed-trade performance.
- A correction to source evidence or a signal definition creates a new immutable result fingerprint
  and does not mutate an existing result.
- Repeating a replay with the same semantic inputs is idempotent and does not create a duplicate
  result fact.
- The result contract does not expose calibrated probabilities, profitability, P&L, risk, or
  execution authority.
- Golden datasets, look-ahead, missing-candle, incomplete-history, provenance, cost-assumption, and
  fingerprint comparison fixtures pass.

## Constraints

- Market Data remains authoritative for historical provider-neutral facts, evidence eligibility,
  coverage, gaps, and source fingerprints.
- Market Intelligence owns deterministic context assembly, signal evaluation, replay interpretation,
  and result provenance.
- Trading Core and Risk Domain remain authoritative for account, risk, authorization, and execution.
- `STRICT_OBSERVED` and `knownAt` semantics remain authoritative and fail closed.
- Future market outcomes remain separate from replay inputs and signal context.
- The human trader remains the final authority for trading decisions.
- AI remains optional and downstream of deterministic replay artifacts.

## Relevant Artifacts

- `docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md`
- `docs/architecture/adr/ADR-057.md`
- `docs/architecture/adr/ADR-058.md`
- `docs/architecture/adr/ADR-059.md`
- `docs/architecture/adr/ADR-060.md`
- `docs/architecture/adr/ADR-061.md`
- `docs/architecture/stories/0111-continuous-research-data-lifecycle/story.md`
- `docs/architecture/stories/0213-controlled-historical-ohlc-reconstruction/story.md`
- `docs/architecture/stories/0247-01-deterministic-evidence-quality-contract/story.md`
- `docs/architecture/stories/0247-02-opportunity-outcome-capture/story.md`

## Validation Expectations

- Golden replay fixtures with stable result fingerprints.
- Exact-cutoff, pre-cutoff, post-cutoff, and `knownAt` look-ahead fixtures.
- Missing-candle, gap, stale, excluded, contradictory, and incomplete-history fixtures.
- Provider/product/timeframe/signal-version provenance tests.
- Explicit spread and transaction-cost assumption tests.
- Immutable correction and duplicate replay tests.
- Targeted `market-intelligence` verification and `git diff --check`.
