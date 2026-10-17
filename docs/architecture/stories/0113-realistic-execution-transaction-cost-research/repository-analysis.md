# Repository Analysis - Story 0113

## Status

Investigation completed and approved by the human engineer for implementation
planning. This document records repository evidence and recommendations; it
does not authorize production implementation or create a new execution
contract.

## Sources and Authority

- The current repository is authoritative for physical behavior and runtime
  state.
- ADR-029, ADR-030, ADR-035, ADR-042, ADR-051, ADR-056, ADR-059, ADR-060,
  ADR-061, ADR-062, and ADR-063 define the relevant architectural boundaries.
- Stories 0107A, 0107C, 0108A, 0108B, 0109B, 0111, 0112A, and 0112B provide
  implementation context and explicit exclusions.
- DevLog was queried but was unavailable because the configured provider
  returned an exhausted-quota error. No historical conclusion relies on that
  unavailable source.

## Investigation Question

Determine what is required to make execution and transaction-cost simulation
more realistic without turning Market Intelligence into a second execution
engine, weakening deterministic Risk, or changing the meaning of
`ObservedMarketOutcome`.

## Current Repository State

The working tree contains unrelated pre-existing changes that are not part of
this investigation:

- `docker-compose.yml`;
- `market-data/src/main/java/com/hope/trading/market_data/service/MarketSynchronizationImpl.java`.

The current runtime was inspected without modifying it. Market Data contains
historical OHLC evidence and an Order Book evidence migration, but the active
database has not applied the Order Book migration. Market Intelligence contains
objective outcomes, immutable feature snapshots, and deterministic research
dataset export. No durable funding evidence, historical fee schedule, or
latency evidence was found.

## Existing Execution Boundary

Trading Core owns execution intent, execution attempts, broker order state,
trade outcomes, PAPER settlement, and P&L projection. The current simulated
adapter is intentionally simple:

- market BUY uses the ask and market SELL uses the bid;
- LIMIT orders are filled immediately when the current simulated state permits;
- no order-book walking, partial-fill model, queue model, latency model, or
  uncertainty model exists;
- PAPER settlement currently records zero fees unless a real execution outcome
  supplies an amount.

This is a valid bounded PAPER behavior but is not a historical execution
simulator. It should not be silently expanded inside Market Intelligence.

## Existing Broker Boundary

Broker Service exposes provider-neutral order and fill contracts and keeps
Kraken-specific details in its adapter. `FillSnapshot` already carries a fee,
but there is no historical fee schedule model, maker/taker policy, funding
ledger, or provider-neutral cost attribution contract. A broker-reported fee is
an observed execution fact, not a substitute for a historical simulation
policy.

## Existing Market Evidence

Market Data provides temporal OHLC evidence with `occurredAt` and `knownAt` and
has the foundation for strict Order Book replay. ADR-063 requires replay from
durable evidence at a cutoff and forbids current provider state as a fallback.

The repository does not currently provide:

- durable funding-rate or funding-payment evidence;
- historical fee schedules with effective intervals and source provenance;
- durable latency or decision-to-acknowledgement observations suitable for
  replay;
- a complete historical market-state contract combining the evidence needed by
  an execution simulator.

The absence of funding is material: it cannot be reconstructed safely after the
fact from OHLC alone. Fee schedules and execution constraints also require
effective-time semantics rather than a current configuration lookup.

## Existing Research Boundary

`ObservedMarketOutcome` represents objective market evolution and must not
contain strategy P&L or simulated execution results. `FeatureSnapshot` and the
research dataset boundary are immutable and time-aware. Python consumes
validated Java-produced datasets and is not authoritative for market facts,
execution semantics, Risk, or transaction-cost calculation.

ADR-042 and ADR-059 therefore imply two distinct concepts:

1. objective market outcome, used to evaluate what the market did;
2. economic execution result, used to evaluate what a specific intent would
   have earned or paid under an explicit simulation policy.

Conflating these concepts would create look-ahead and double-counting risks.

## Gap Classification

| Capability | Status | Evidence | Consequence |
| --- | --- | --- | --- |
| Objective market outcome | READY | `ObservedMarketOutcome`, strict cutoff model | Reusable for market evolution, not P&L |
| Basic PAPER market execution | PARTIAL | `SimulatedExecutionAdapter` | Useful for bounded PAPER behavior, not historical replay |
| Historical Order Book replay | PARTIAL | 0112A foundation and ADR-063 | Runtime migration/transport boundary still incomplete |
| Durable funding evidence | MISSING | No funding model/table/adapter found | Historical funding cannot be reconstructed later |
| Historical fee schedule | MISSING | Only observed fill fee exists | Simulation cannot safely use current fee configuration |
| Latency evidence/model | MISSING | No durable temporal execution evidence found | Cannot claim latency realism |
| Partial-fill/queue model | MISSING | No policy or state model found | Limit-order realism must remain explicitly bounded |
| Transaction-cost result | MISSING | No provider-neutral economic result contract found | Risk of ad hoc P&L calculations |
| Java authoritative simulation | PARTIAL | Deterministic Java domain and Python dataset consumer exist | New contract must remain on Java side |
| Python research consumption | READY | Dataset loader/experiment boundary exists | Should consume validated economic results, not invent them |

## Recommended Responsibility Model

### Market Data

- Acquire and persist provider-neutral funding evidence, fee schedule evidence,
  market constraints, and Order Book evidence.
- Preserve `occurredAt`, `knownAt`, effective intervals, source identity,
  normalization version, and evidence fingerprints.
- Expose strict historical reads at an evaluation cutoff.

### Market Intelligence

- Assemble a bounded, replayable `ReferenceMarketState` at the evaluation cutoff.
- Interpret market evidence and produce recommendations or research features.
- Do not own order matching, settlement, Risk, authorization, or broker calls.

### Trading Core / Risk Domain

- Own `StrategyIntent`, deterministic Risk evaluation, execution simulation
  orchestration, economic result calculation, and PAPER settlement policy.
- Keep the simulation policy explicit, versioned, and independent from current
  provider state.
- Treat missing required evidence as an explicit degraded/unsupported result,
  never as zero cost.

### Broker Service

- Supply provider-neutral live execution/fill facts and provider-specific
  mappings.
- Never become the owner of historical strategy simulation or profitability
  decisions.

### Research / Python

- Consume validated Java-produced datasets and economic results.
- Run analysis and experiments without becoming authoritative for market facts,
  execution, Risk, or cost semantics.

## Proposed Domain Separation

The following names are investigation outputs, not approved production types:

- `ReferenceMarketState`: immutable market facts available at a cutoff,
  including the exact evidence scope and provenance.
- `StrategyIntent`: immutable side, quantity, order type, price constraints,
  time-in-force, and policy references.
- `SimulatedExecution`: fills, fill status, execution timestamps, and explicit
  model version, without embedding strategy outcome.
- `TransactionCosts`: fee, spread capture/loss, modeled slippage, funding,
  and other cost components with independent evidence and missingness.
- `EconomicResult`: gross movement, costs, net result, and degraded status,
  linked to the intent, execution model, and objective market outcome.

`EconomicResult` must not be stored as `ObservedMarketOutcome`, and the same
spread or slippage component must not be charged both in the fill price and in
the cost ledger.

## Double-Counting Prevention

The future contract must choose exactly one representation for each effect:

- execution price impact is represented either by the simulated fill price or
  by an explicit slippage cost, never both;
- bid/ask crossing is represented either by the fill price relative to the
  reference price or by a spread-cost component, never both;
- broker-reported fees are used for observed live fills, while simulated fees
  use a versioned historical schedule;
- funding is applied only for eligible holding intervals and only from funding
  evidence known under the replay policy;
- objective market movement remains separate from all strategy-specific costs.

Each component should carry a calculation version and source fingerprint so a
replay can prove which representation was used.

## Temporal and Reproducibility Requirements

Every historical simulation must define:

- evaluation cutoff and decision timestamp;
- the latest evidence allowed by `knownAt`;
- market evidence interval and source fingerprints;
- fee schedule/funding effective interval;
- execution model and policy version;
- deterministic rounding and quantity/price precision;
- explicit status for unavailable, stale, insufficient, or unsupported inputs.

Current provider state, current fee configuration, or current funding must not
be used as an implicit fallback. Equal typed inputs, policy parameters, and
versions must produce equal fills, cost components, economic results, and
fingerprints.

## Architectural Risks

- Adding simulation logic to Market Intelligence would duplicate Trading Core
  authority and create divergent execution semantics.
- Reusing `ObservedMarketOutcome` for simulated P&L would corrupt the objective
  outcome contract and research labels.
- Applying current fee/funding configuration to historical samples would create
  look-ahead and non-reproducible results.
- Modeling slippage without durable Order Book evidence would produce an
  unjustified precision claim.
- Introducing partial fills or queue position without an explicit policy would
  make backtest results appear more realistic while being less auditable.
- Automatically substituting zero for missing costs would bias research and
  understate risk.

## Recommendation

**DECISION: B - foundation before realism claims.**

Do not implement a broad realistic-execution simulator yet. First implement the
minimum durable evidence and contract foundation needed to avoid false
precision:

1. durable funding evidence and strict historical retrieval;
2. historical fee schedule/effective-time evidence;
3. a provider-neutral reference-market-state contract with provenance;
4. explicit transaction-cost and degraded-result semantics;
5. a narrow, versioned market-order simulation policy using only evidence that
   is actually available;
6. separate PAPER settlement integration after the simulation contract is
   validated.

The first implementation story should prioritize evidence capture that cannot
be reconstructed later, especially funding. Limit-order queueing, latency
calibration, and sophisticated partial-fill behavior should remain deferred
until the repository contains sufficient evidence and an approved policy.

## Validation Expectations

Before implementation is considered complete, validation should include:

- deterministic tests for each cost component and missingness state;
- temporal tests for `occurredAt`, `knownAt`, effective intervals, and cutoff;
- tests proving spread/slippage/fee/funding are not double-counted;
- replay tests proving current provider state is never used as fallback;
- contract tests separating objective outcomes from economic results;
- Java verification for affected services and `git diff --check`;
- representative dataset export and Python read-only consumption tests;
- runtime validation only after the required migrations and evidence capture
  paths exist.
