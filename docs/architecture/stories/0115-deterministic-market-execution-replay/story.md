# Story 0115 - Deterministic MARKET Execution Replay

## Metadata

**ID:** `0115`

**Title:** Deterministic MARKET Execution Replay

**Status:** Draft

**Related Stories:** `0112A`, `0113`, `0114`

**Related ADRs:** `ADR-063`, `ADR-064`, `ADR-057`, `ADR-058`, `ADR-059`, `ADR-060`, `ADR-061`, `ADR-062`

This Story is created at the human engineer's request. It defines the first
implementation boundary for historical MARKET execution replay. It does not
grant approval for implementation, planning, or integration workflow gates.

---

## Goal

Given a provider-neutral MARKET `StrategyIntent`, an explicit historical
execution cutoff, and strict historical Order Book evidence, reconstruct the
eligible visible book and deterministically simulate:

- the quantity that could be filled;
- the consumed prices and quantities;
- the volume-weighted average execution price;
- any unfilled remainder;
- the execution status and reason;
- the historical evidence provenance;
- the deterministic execution fingerprint.

This Story establishes historical execution truth. It does not establish
complete economic truth.

Historical fees may remain `UNAVAILABLE`. Applicable funding remains
`PROVIDER_BLOCKED` or `UNAVAILABLE`. Neither missing cost may become zero.

---

## Context

ADR-063 establishes a provider-neutral immutable Order Book evidence stream and
strict replay using one trusted reconstruction sequence with `knownAt <= T0`.
ADR-064 assigns historical execution calculation to Trading Core while keeping
Market Data authoritative for market evidence and replay truth.

Trading Core already contains the provider-neutral economic foundation:

- `ReferenceMarketState`;
- `StrategyIntent`;
- `SimulatedExecution`;
- `TransactionCosts`;
- `CostEvidence`;
- `EconomicResult`;
- `MarketOrderEconomicEvaluator`.

The existing contracts are partial and must be evolved or composed minimally.
This Story must reuse them and must not introduce a parallel execution or
economic domain.

The existing Order Book replay remains the only admissible source of historical
visible liquidity. Current ticker state, current Order Book state, REST
fallbacks, and provider calls are not historical execution evidence.

---

## Problem

Trading OS can preserve and replay historical Order Book evidence, but it does
not yet answer the deterministic execution question for a hypothetical MARKET
intent. Without this capability, research could silently:

- fill more quantity than visible liquidity supports;
- use the wrong side or wrong price priority;
- lose the unfilled remainder;
- use a current book for a historical cutoff;
- apply evidence known after the cutoff;
- walk an invalid or gapped reconstruction;
- collapse a multi-level fill into the best price;
- treat no fill as a zero-price execution;
- invent fees, funding, liquidity, or market impact.

---

## Bounded V1 Scope

The initial operational scope remains:

```text
provider: Kraken
market: BTC/USD
instrument: LINEAR_PERPETUAL
provider symbol: PF_XBTUSD
visible depth: 25 levels per side
order type: MARKET
modeled latency: zero, explicit and versioned
```

The domain contracts remain provider-neutral. Kraken concepts terminate at
Market Data and provider adapters.

---

## Scope

- Consume the existing strict provider-neutral Order Book replay boundary.
- Assemble a provider-neutral historical executable market state at an explicit
  execution cutoff.
- Preserve `occurredAt` and `knownAt` semantics without future leakage.
- Support `MARKET` intents only.
- Walk asks from lowest to highest for BUY orders.
- Walk bids from highest to lowest for SELL orders.
- Preserve requested, filled, and remaining quantities.
- Preserve individual consumed level fills where the canonical contract allows;
  otherwise make the minimum domain evolution required to retain them.
- Calculate deterministic VWAP with `BigDecimal`.
- Represent full fill, partial fill with insufficient visible depth, no fill,
  invalid replay, unavailable replay, and unsupported order type using the
  canonical status vocabulary.
- Preserve execution cutoff, replay identity, source provenance, normalization
  version, execution model version, and deterministic fingerprint.
- Keep execution calculation in Trading Core and historical market truth in
  Market Data.
- Add focused deterministic, temporal, trust, provenance, and fingerprint tests.
- Execute at least one real historical replay against persisted evidence when
  runtime evidence is healthy and the replay path is available.

---

## Out of Scope

- Commission calculation or HistoricalFeeSchedule acquisition.
- ResearchFeePolicy implementation.
- Funding acquisition, funding calculation, or funding settlement.
- Economic research dataset or Python backtesting integration.
- LIMIT, STOP, or STOP_LIMIT execution.
- Queue position, fill probability, exchange matching, latency calibration, or
  dynamic market impact.
- Trade-through, hidden liquidity, cancellation, or adverse-selection models.
- Infinite-liquidity or beyond-depth extrapolation.
- PAPER migration or operational execution changes.
- Risk authorization, live broker execution, or execution-state integration.
- Changes to `ObservedMarketOutcome`, `research-dataset-v1`, or
  `feature-projection-v1`.
- New services, public APIs, streaming platforms, or broad multi-provider
  rollout.
- Provider-specific DTOs or Kraken execution logic in Trading Core.
- Automatic commit, push, merge, PR creation, or deployment.

---

## Execution Semantics

### MARKET Only

`MARKET` is the only supported order type. Unsupported order types must return
the canonical unsupported result and must not be approximated as MARKET.

### Side and Price Priority

BUY consumes eligible asks in ascending price order. SELL consumes eligible bids
in descending price order. The implementation must not depend on database row
order or provider serialization order.

### Quantities

The requested quantity must be preserved exactly. Filled quantity may only come
from consumed eligible visible levels. The remainder is always:

```text
remainingQuantity = requestedQuantity - filledQuantity
```

If visible executable quantity is insufficient, the result must retain the
positive remainder and use the canonical partial/insufficient-depth status.

If no eligible opposite-side liquidity exists, filled quantity is zero and no
execution price is fabricated.

### VWAP

For a non-empty fill:

```text
VWAP = sum(price * filled quantity) / sum(filled quantity)
```

The authoritative calculation must use `BigDecimal` and repository-standard
precision conventions. An empty execution must not use zero as a substitute for
an absent VWAP unless the canonical contract explicitly requires that model.

### Spread and Visible-Depth Slippage

BUY execution begins at the ask and SELL execution begins at the bid. Spread
crossing is therefore represented by execution prices. Price deterioration from
walking deeper levels is represented by VWAP. Neither effect may receive a
second generic cost deduction.

V1 does not claim that the hypothetical order caused a market reaction.

---

## Historical Evidence and Replay Trust

The execution cutoff is explicit. V1 may use zero modeled latency only through an
immutable, versioned execution policy; it must not be described as measured
exchange latency.

Eligible evidence must satisfy the applicable ADR rules, including:

```text
knownAt <= executionCutoff
```

`occurredAt` identifies provider/event time. `knownAt` identifies when Trading OS
could defensibly use the evidence. Evidence with an earlier `occurredAt` but a
later `knownAt` must not affect the replay.

Execution requires a trusted reconstructed book. A sequence gap, invalid
checkpoint, missing trusted snapshot, checksum/trust failure, or unavailable
replay must fail closed. The implementation must not walk a corrupted book,
cross sequence breaks, interpolate missing deltas, or use current state as a
fallback.

Sparse DELTAs and deleted levels must follow the canonical Market Data replay
semantics. Trading Core must not reimplement Kraken delta behavior.

---

## Ownership and Boundaries

```text
Market Data
    -> normalized historical Order Book evidence and strict replay

Trading Core
    -> StrategyIntent, execution policy, book walking, fills, VWAP, status,
       provenance, and execution fingerprint

Broker Service
    -> provider/account fee truth and live provider execution facts

Market Intelligence
    -> deterministic interpretation and research dataset orchestration

Python
    -> downstream statistical research only
```

Trading Core must consume a provider-neutral Market Data port or internal client.
Domain code must not call Kraken, Market Data persistence, JDBC, JPA, or a
provider DTO directly. The implementation must not create a circular service
dependency.

---

## Execution Result Contract

The canonical immutable execution result must preserve, where supported by the
current domain:

- market and side;
- order type;
- requested, filled, and remaining quantities;
- individual consumed fills;
- VWAP or explicit absence;
- execution cutoff;
- execution status and reason;
- execution model version;
- reference-state/replay identity;
- evidence provenance and fingerprint;
- deterministic execution fingerprint.

The smallest compatible evolution of `SimulatedExecution` is preferred. Do not
add fields merely because they appear in this Story if an existing canonical
provenance or value object already represents them.

The fingerprint must exclude database IDs, random UUIDs, request IDs, scheduler
timestamps, and other accidental values. It must change when semantic intent,
eligible book, consumed fills, or execution model version changes.

---

## Fees and Funding Boundary

Valid execution does not imply complete economics:

```text
execution truth = available
economic completeness = incomplete
```

This Story must not calculate commission or funding. Missing applicable fees and
funding remain explicit unavailable/provider-blocked states and must never become
zero, current values, predicted values, or values from another trade.

---

## Acceptance Criteria

- [ ] A provider-neutral historical executable book is assembled from the
      existing strict replay result at an explicit cutoff.
- [ ] Only `MARKET` intents are supported; unsupported order types fail without
      approximation.
- [ ] BUY consumes asks lowest-to-highest and SELL consumes bids
      highest-to-lowest deterministically.
- [ ] Requested quantity is preserved exactly.
- [ ] Filled quantity is derived only from eligible visible liquidity.
- [ ] Remaining quantity is explicit for partial and unfilled results.
- [ ] A multi-level fill preserves consumed level quantities/prices where the
      canonical contract supports them.
- [ ] VWAP uses deterministic `BigDecimal` arithmetic.
- [ ] No-fill results do not fabricate an execution price or zero VWAP.
- [ ] Visible-depth exhaustion produces an explicit partial/insufficient-depth
      result and never fabricates a full fill.
- [ ] Invalid, unavailable, gapped, or untrusted replay fails closed.
- [ ] Evidence with `knownAt > executionCutoff` cannot affect execution.
- [ ] Current provider state and current Order Book state are never historical
      fallbacks.
- [ ] Execution model version, cutoff, replay provenance, normalization version,
      source fingerprint, and execution fingerprint are retained.
- [ ] Equal semantic inputs produce equal execution results and fingerprints.
- [ ] Changed quantity, eligible book, cutoff, or model version changes the
      semantic result or fingerprint as applicable.
- [ ] Spread crossing and visible-depth deterioration are not double-counted.
- [ ] Fees and funding are not invented or converted to zero.
- [ ] `ObservedMarketOutcome`, `research-dataset-v1`, `feature-projection-v1`,
      PAPER behavior, and operational Risk behavior remain unchanged.
- [ ] The required 2 BTC versus 1.7 BTC visible-ask scenario produces a 1.7 BTC
      fill, 0.3 BTC remainder, and explicit partial/insufficient-depth status.
- [ ] Focused tests and relevant module validation pass.
- [ ] `git diff --check` passes and unrelated worktree changes are preserved.

---

## Required Test Scenarios

- Single-level full BUY.
- Multi-level full BUY with deterministic VWAP.
- Multi-level full SELL with symmetric bid priority.
- Partial fill where visible quantity is less than requested quantity.
- Empty eligible opposite-side book.
- Invalid replay caused by sequence gap or missing trusted checkpoint.
- Evidence with eligible `occurredAt` but ineligible `knownAt`.
- Unsupported LIMIT intent.
- Repeated identical replay proving deterministic result and fingerprint.
- Different requested quantities against the same book.
- Different cutoffs against different reconstructed states.
- No spread double counting and no generic duplicate slippage deduction.
- No fee, funding, P&L, or current-state leakage.

---

## Runtime Proof

After focused tests, and only if persisted evidence is healthy, execute one real
historical replay without mutating Market Data. Report:

- market and cutoff;
- side and requested quantity;
- filled and remaining quantities;
- VWAP;
- consumed level count;
- execution status;
- execution model version;
- replay/evidence fingerprint;
- execution fingerprint.

Repeat the same replay and verify the semantic result and fingerprint are
identical. A partial-fill proof is required when safely available but must not be
forced through artificial or unreasonable inputs.

---

## Validation Expectations

- Run focused Trading Core execution replay tests first.
- Run affected Trading Core module tests and verification.
- Run relevant Market Data replay regression tests if the transport contract
  changes.
- Run the applicable repository quality validation and SonarQube checks when
  configured.
- Run `git diff --check`, `git status`, and review the complete diff.
- Report blocked or unavailable runtime proof honestly.

---

## Documentation and Follow-up

The implementation report must document:

- MARKET-only behavior;
- visible-depth limitation;
- explicit zero-latency V1 policy;
- partial-fill and insufficient-depth semantics;
- absence of queue and market-impact modeling;
- independent unavailable fee and funding states;
- runtime proof and validation results.

Do not amend ADR-063 or ADR-064 unless implementation discovers a genuine
architectural contradiction. Such a contradiction requires `ADR_AMENDMENT_REQUIRED`
and human guidance.

The implementation must recommend exactly one next primary Story based on the
actual remaining blocker. Candidate follow-ups include forward-only fee capture,
economic-result integration, economic research dataset integration, or execution
replay lifecycle integration.

---

## Definition of Done

- [ ] Repository Analysis approved by the human engineer.
- [ ] Implementation Plan approved when required.
- [ ] Implementation remains within this Story and accepted ADR boundaries.
- [ ] Documentation reconciliation is complete.
- [ ] Focused and relevant validation passes or failures are reported honestly.
- [ ] Code Review is completed.
- [ ] Engineering Report is completed.
- [ ] Human commit and integration decisions are completed.
