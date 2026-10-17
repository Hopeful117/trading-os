# Investigation Plan - Story 0113

This decomposition follows the repository investigation and was approved by
the human engineer as planning input. It does not authorize production changes
outside explicitly approved follow-up implementation Stories.

## Status

ADR-064 was accepted by the human engineer before implementation. The bounded
provider-neutral economic contract tranche is present in the current worktree,
but ADR acceptance alone does not replace the required explicit follow-up Story
authorization before integration.

Completed in this tranche:

- lifecycle-capable economic domain contracts;
- explicit execution status and cost-component status;
- first-class cost completeness separate from execution status;
- deterministic lifecycle accounting and fingerprints;
- focused contract tests.

Historical evidence adapters, replay, runtime fee/funding acquisition, PAPER
integration, and research dataset integration remain deferred.

## Phase 1 - Evidence Foundation

1. Define provider-neutral funding evidence with `occurredAt`, `knownAt`,
   instrument identity, funding interval, rate/payment semantics, source
   provenance, normalization version, and deterministic fingerprint.
2. Define historical fee schedule evidence with maker/taker or equivalent fee
   semantics, effective interval, account/instrument scope, source provenance,
   and explicit unknown behavior.
3. Extend strict historical retrieval contracts without exposing provider DTOs
   or using current state as fallback.
4. Add focused persistence, temporal eligibility, provenance, and replay tests.

## Phase 2 - Simulation Contract

1. Define immutable `ReferenceMarketState`, `StrategyIntent`,
   `SimulatedExecution`, `TransactionCosts`, and `EconomicResult` contracts, or
   approve narrower names with the same separation of responsibilities.
2. Version the execution and cost policies independently from market evidence.
3. Define one representation for spread crossing and slippage to prevent double
   counting.
4. Define explicit `AVAILABLE`, `DEGRADED`, `INSUFFICIENT_DATA`, `STALE`, and
   `UNSUPPORTED` outcomes as appropriate to the final contract.
5. Keep all deterministic calculations in Java and make the result replayable
   from immutable inputs.

## Phase 3 - Narrow Realism

1. Implement only the market-order policy justified by available evidence.
2. Use strict Order Book replay when available; otherwise return an explicit
   unsupported/degraded result rather than fabricating slippage.
3. Apply historical fee and funding evidence only when the cutoff and effective
   intervals make them eligible.
4. Preserve a complete calculation fingerprint and policy version.

## Phase 4 - PAPER Integration

1. Decide whether PAPER settlement consumes the same economic-result contract or
   a separate adapter over it.
2. Preserve Trading Core ownership of Risk, execution state, idempotency, and
   settlement.
3. Do not make Market Intelligence call brokers or settle trades.
4. Validate that live observed fees and simulated fees remain distinguishable.

## Deferred Work

- Queue-position modeling.
- Latency-calibrated fills.
- Provider-specific matching behavior.
- Broad limit-order and sophisticated partial-fill realism.
- Catalog-wide or multi-provider rollout.
- Python-owned economic calculations.
- Any change to the meaning of `ObservedMarketOutcome`.

## Required Decisions Before Implementation

- Which cost effects are represented in fill price versus the cost ledger?
- Is funding modeled as a cost component, an economic transfer, or both with
  separate accounting semantics?
- What minimum evidence makes a market-order simulation `AVAILABLE`?
- Which missing evidence produces `DEGRADED` versus `UNSUPPORTED`?
- Which service owns the public/internal contract for economic results?
- Is PAPER settlement required in the first implementation story or deferred?
