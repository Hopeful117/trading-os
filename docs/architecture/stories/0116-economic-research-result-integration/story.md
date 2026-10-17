# Story 0116 - Economic Research Result Integration

## Metadata

**ID:** `0116`
**Title:** Economic Research Result Integration
**Status:** Approved
**Related Stories:** `0113`, `0114`, `0115`
**Related ADRs:** `ADR-057`, `ADR-058`, `ADR-059`, `ADR-060`, `ADR-061`, `ADR-062`, `ADR-063`, `ADR-064`

This Story connects the existing historical evidence, deterministic MARKET
execution replay, and provider-neutral economic foundations into one bounded
historical economic evaluation path. It does not authorize implementation until
the Repository Analysis and Implementation Plan have been approved by the human
engineer.

---

## Goal

Given a historical `StrategyIntent`, deterministic entry and exit executions,
and admissible cost evidence, calculate the economic conclusions that are
legitimately knowable while preserving all remaining uncertainty explicitly.

The target lifecycle is:

```text
StrategyIntent
    -> Entry SimulatedExecution
    -> Holding interval
    -> Exit SimulatedExecution
    -> Explicit TransactionCosts / CostEvidence
    -> EconomicResult
```

For the current historical Kraken perpetual research path, a valid result may
contain:

```text
Market movement          = AVAILABLE
Entry execution          = AVAILABLE
Exit execution           = AVAILABLE
Execution-adjusted P&L   = AVAILABLE
Historical commission    = UNAVAILABLE
Historical funding       = UNAVAILABLE
Fully-net P&L            = UNAVAILABLE
Cost completeness        = INCOMPLETE
```

Missing evidence must never be converted to zero merely to produce `NetPnL`.

---

## Context

The repository now contains the deterministic foundations established by the
preceding Stories:

```text
Historical Market Evidence
    -> Strict Historical Order Book Replay
    -> Deterministic MARKET Execution Replay
    -> SimulatedExecution

Provider-neutral Economic Contract
    -> CostEvidence / CostCompleteness
    -> EconomicResult
```

`ADR-064` assigns historical execution calculation, explicit cost application,
cost completeness, and economic results to Trading Core. Market Data remains the
authority for normalized historical evidence and strict replay. Broker Service
remains the authority for provider/account fee truth and live provider facts.

The current economic foundation is partial and must be composed minimally. This
Story must reuse existing concepts rather than creating a parallel backtest or
economic domain.

---

## Problem

Trading OS can now reconstruct historical execution truth, but it does not yet
connect entry execution, holding interval, exit execution, and explicit cost
availability into one deterministic `EconomicResult`.

Without this integration, the system could incorrectly:

- calculate economics from requested instead of filled quantity;
- treat a partial entry as a full position;
- treat a partial exit as a closed lifecycle;
- reuse the entry book for the exit;
- use OHLC or current prices as synthetic execution prices;
- use current fee schedules or unrelated trade fees historically;
- convert missing commission or funding to zero;
- produce authoritative `NetPnL` or `NetReturn` with incomplete costs;
- conflate `NOT_APPLICABLE` with `UNAVAILABLE`;
- contaminate `ObservedMarketOutcome`, PAPER, Risk, or research datasets.

---

## Scope

- Add the smallest application-level orchestration for a historical economic
  lifecycle, using the existing economic domain types.
- Use the deterministic MARKET execution capability for both entry and exit.
- Keep entry and exit semantics explicit; do not represent them as untyped list
  positions.
- Use an explicit historical exit cutoff or existing canonical holding horizon.
- Derive actual exposure from entry `filledQuantity`, never from requested entry
  quantity.
- Support partial entry, partial exit, residual exposure, and no-fill cases
  without synthetic closure.
- Calculate reference market movement separately from strategy economics when the
  existing contract supports it.
- Calculate execution-adjusted P&L from entry and exit VWAPs, closed quantity,
  direction, and existing product/accounting primitives.
- Reuse canonical `TransactionCosts`, `CostEvidence`, `CostCompleteness`, and
  `EconomicResult` semantics.
- Preserve unavailable historical commission and provider-blocked funding as
  explicit states.
- Derive cost completeness from evidence states; callers must not select
  `FULLY_COSTED` arbitrarily.
- Make authoritative `NetPnL` and `NetReturn` unavailable when required cost
  evidence or a legitimate denominator is absent.
- Preserve decision time, entry cutoff, exit cutoff, research/cost cutoff, and
  `knownAt` semantics without future leakage.
- Produce a deterministic economic fingerprint from semantic inputs, including
  execution fingerprints, cost statuses/evidence, and model versions.
- Keep the first integration in-memory unless an accepted existing persistence
  contract requires persistence.
- Add focused lifecycle, partial-fill, temporal, cost-completeness, and
  fingerprint tests.

---

## Out of Scope

- New Order Book replay semantics or book-walking algorithms.
- Historical fee-schedule acquisition or forward fee capture.
- Funding acquisition, reconstruction, or settlement.
- LIMIT, STOP, or STOP_LIMIT execution.
- Latency calibration, queue simulation, fill probability, or market impact.
- Generic backtest engine, strategy discovery, signal selection, or horizon
  optimization.
- PAPER integration, live trading, broker execution, or Risk authorization.
- Public REST API or new service.
- Economic research dataset, `research-dataset-v2`, or `feature-projection-v2`.
- Python backtesting, Jupyter integration, ML, or LLM functionality.
- Changes to `ObservedMarketOutcome`, `research-dataset-v1`, or
  `feature-projection-v1`.
- Provider-specific Kraken DTOs or provider leakage into the economic domain.
- Automatic commit, push, merge, PR creation, or deployment.

---

## Lifecycle Semantics

### Entry and Exit

For a LONG lifecycle:

```text
entry = BUY
exit  = SELL
```

For a SHORT lifecycle:

```text
entry = SELL
exit  = BUY
```

The exit must use a distinct historical cutoff and a fresh deterministic replay.
It must not reuse the entry book or an OHLC/current-price shortcut.

### Partial Exposure

If entry requests `2 BTC` and fills `1.7 BTC`, exposure is `1.7 BTC` and the
`0.3 BTC` remainder is not an open position.

If `1.7 BTC` is open and the exit fills `1.2 BTC`, closed quantity is `1.2 BTC`
and residual exposure is `0.5 BTC`. The lifecycle must not be represented as
fully closed and must not fabricate a result for the residual exposure.

If entry fills zero, no exit is simulated as though a position existed. If exit
has no eligible liquidity, the open/residual exposure remains explicit.

### Economic Accounting

Execution-adjusted economics must use actual entry and exit execution prices:

```text
LONG  = (exitVWAP - entryVWAP) * closedQuantity
SHORT = (entryVWAP - exitVWAP) * closedQuantity
```

These formulas are subject to existing product/accounting primitives and must
not duplicate canonical P&L logic.

Spread crossing and visible-depth deterioration are already represented by the
execution fills/VWAP. No second spread or generic slippage deduction may be
added.

---

## Cost Semantics

- Historical hypothetical commission remains `UNAVAILABLE` when no defensible
  historical schedule exists.
- Actual fees from unrelated historical trades must not be reused for the
  hypothetical lifecycle.
- Current Kraken schedules must not be backdated.
- Funding remains `UNAVAILABLE` or `PROVIDER_BLOCKED` for applicable perpetual
  intervals when evidence is absent.
- Funding is `NOT_APPLICABLE` for SPOT.
- `Commission AVAILABLE(rate=0)` must remain distinct from unavailable
  commission evidence.
- `Funding NOT_APPLICABLE` must remain distinct from unavailable funding.
- `CostCompleteness` is derived from the canonical component states.
- Valid execution truth remains available independently of incomplete cost truth.
- No authoritative fully-net `NetPnL` or `NetReturn` may be produced unless
  the canonical completeness and denominator requirements are satisfied.
- No `NaN`, implicit null, or zero may represent unknown economics when the
  canonical contract provides an explicit availability state.

---

## Temporal Integrity

The implementation must preserve the distinct clocks:

```text
Decision time
Execution time / cutoff
Outcome or evaluation time
```

Entry replay must enforce its own cutoff, exit replay must enforce its own
cutoff, and cost evidence must respect its effective interval, `knownAt`, and
research cutoff. Evidence known after the applicable cutoff must not improve the
result or cost completeness.

---

## Ownership Boundaries

```text
Market Data
    -> historical normalized market evidence and strict replay

Trading Core
    -> lifecycle orchestration, execution truth, cost application,
       completeness, and EconomicResult

Broker Service
    -> provider/account fee truth and live provider execution facts

Market Intelligence
    -> interpretation and research orchestration

ObservedMarketOutcome / datasets
    -> remain objective/frozen boundaries
```

The integration must not mutate accounts, PAPER state, Risk state, or persisted
market evidence.

---

## Acceptance Criteria

- [ ] A bounded application service evaluates entry execution, holding interval,
      exit execution, explicit costs, and `EconomicResult`.
- [ ] Existing `StrategyIntent`, execution replay, and economic contracts are
      reused; no parallel strategy or economic domain is introduced.
- [ ] Entry and exit semantics are explicit and directionally correct for LONG
      and SHORT lifecycles.
- [ ] Entry and exit use independent historical execution cutoffs and replays.
- [ ] Entry exposure is based on actual filled quantity.
- [ ] Partial entry preserves requested, filled, and remainder quantities.
- [ ] Partial exit preserves closed quantity and residual exposure.
- [ ] No-fill entry and no-liquidity exit do not create synthetic closure or
      fabricated prices.
- [ ] Execution-adjusted P&L uses actual entry/exit VWAPs and closed quantity.
- [ ] Reference movement and strategy economics remain distinct.
- [ ] Spread crossing and depth deterioration are not double-counted.
- [ ] Historical commission remains explicitly unavailable when unsupported.
- [ ] Historical funding remains unavailable/provider-blocked for applicable
      perpetual intervals without evidence.
- [ ] SPOT funding is represented as not applicable where supported.
- [ ] Missing commission/funding never becomes zero.
- [ ] Cost completeness is derived and incomplete perpetual results remain
      distinct from fully costed results.
- [ ] Authoritative `NetPnL` requires full applicable cost completeness.
- [ ] Authoritative `NetReturn` requires legitimate `NetPnL` and denominator
      semantics.
- [ ] Future Order Book or cost evidence cannot affect an earlier evaluation.
- [ ] Economic result fingerprints are deterministic and sensitive to semantic
      execution, cost, and policy/version changes.
- [ ] `ObservedMarketOutcome`, frozen datasets, PAPER behavior, Risk behavior,
      fee acquisition, and funding acquisition remain unchanged.
- [ ] No public API or persistence model is added without an existing canonical
      architectural requirement.
- [ ] Focused tests and relevant Trading Core validation pass.
- [ ] `git diff --check` passes and unrelated worktree changes are preserved.

---

## Required Test Scenarios

- Fully filled LONG perpetual with positive execution-adjusted P&L and
  unavailable commission/funding; `NetPnL` remains unavailable.
- Losing gross execution-adjusted P&L with unavailable costs.
- Partial entry: requested `2 BTC`, filled `1.7 BTC`.
- Partial exit: `1.7 BTC` exposure, `1.2 BTC` exit, `0.5 BTC` residual.
- No entry fill.
- Entry succeeds but exit has no executable liquidity.
- Symmetric LONG and SHORT side handling.
- SPOT reference with available commission and not-applicable funding when the
  existing contract permits complete supplied evidence.
- Available zero commission versus unavailable commission.
- SPOT not-applicable funding versus perpetual unavailable funding.
- Spread and visible-depth deterioration are not deducted twice.
- Distinct entry and exit cutoffs with independent replay evidence.
- Future cost evidence cannot upgrade completeness.
- Repeated identical lifecycle produces identical values, statuses, and
  fingerprint.
- Changing execution, cost evidence/status, or model version changes identity.

---

## Runtime Proof

If persisted evidence remains healthy and authenticated service access is
available, execute one bounded historical economic evaluation without mutating
Market Data. Prefer a small quantity likely to be fully executable at both
cutoffs.

Report without secrets:

- market and product;
- direction and requested quantity;
- entry and exit cutoffs;
- entry and exit filled quantities and VWAPs;
- closed quantity and residual exposure;
- execution-adjusted P&L;
- commission and funding statuses;
- cost completeness;
- NetPnL availability;
- EconomicResult fingerprint;
- repeated-result identity check.

Do not call a positive execution-adjusted result a profitable trade, strategy,
edge, or alpha when required costs remain unavailable.

---

## Validation Expectations

- Run focused historical economic lifecycle tests first.
- Run affected Trading Core tests and `verify` according to module conventions.
- Run Market Data replay regression tests if the client contract changes.
- Run Broker tests only if shared fee evidence contracts are touched.
- Run applicable quality validation and SonarQube checks when configured.
- Run `git diff --check`, `git status`, and review the complete diff.
- Report unavailable integration dependencies and runtime proof blockers honestly.

---

## Documentation and Follow-up

The implementation report must state explicitly:

- execution truth may be complete while cost truth is incomplete;
- historical fees remain unavailable where unsupported;
- funding remains provider-blocked/unavailable;
- missing costs never become zero;
- partial fills determine actual exposure;
- residual exposure prevents false closed-trade economics;
- authoritative NetPnL/NetReturn availability and completeness semantics;
- runtime proof and validation outcomes.

Do not amend ADR-064 unless implementation reveals a genuine architectural
contradiction. Such a contradiction requires `ADR_AMENDMENT_REQUIRED` and human
guidance.

At completion, recommend exactly one primary next Story based on the actual
remaining blocker. Candidate follow-ups include economic research dataset,
forward-only fee schedule capture, historical trade-fee enrichment, or an
economic evaluation batch lifecycle.

---

## Definition of Done

- [x] Repository Analysis approved by the human engineer.
- [x] Implementation Plan approved when required.
- [x] Implementation remains within this Story and accepted ADR boundaries.
- [x] Documentation reconciliation is complete.
- [x] Focused and relevant validation passes or failures are reported honestly.
- [x] Independent code review is completed.
- [x] Engineering report is completed.
- [ ] Human commit and integration decisions remain separate from implementation.
