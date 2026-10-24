# Repository Analysis - Story 0160

## Baseline

- `HEAD`: `feed7c344899d1fec0e309ef7ab6a20538943ea2`
- `origin/main`: `feed7c344899d1fec0e309ef7ab6a20538943ea2`
- Story 0159 is merged in PR #179.
- Worktree is clean on `main`.
- No DevLog context was available: the Story Agent request failed with an
  upstream provider HTTP 429. No DevLog evidence is used as authority.
- Obsidian was not consulted; no relevant repository-owned dependency on vault
  knowledge was identified during this analysis.

## Existing Financial Authorities

### Trade Plan

Market Intelligence owns the immutable Trade Plan and exact version lineage:

- `market-intelligence/.../domain/tradeplan/TradePlan.java`
- `ExecutionParameters` contains direction, entry, Stop, targets, quantity,
  position sizing, Risk/Reward, and provider-neutral instrument identity.
- `TradePlanResponse` already exposes entry, Stop, first/all targets, quantity,
  notional, monetary Risk, and numeric Risk/Reward.
- `TradePlanRiskPort.Snapshot` is the Trading Core handoff used by Risk and is
  already bound to Trade Plan id/version and account ownership context.

### Existing Derived Economics

There are two existing gross arithmetic implementations:

- `TradingCalculatorServiceImpl` computes absolute price distance times quantity,
  gross Reward, and a two-decimal ratio.
- `TradePlanBuilder` computes the first-target absolute distance and stores a
  two-decimal `RiskReward`.

They are not currently a single provider-neutral authoritative projection
contract. The new capability must not duplicate these formulas in Angular. The
implementation plan must decide whether the Risk Domain receives a reusable
gross outcome calculator or whether the existing calculator can be moved behind
the financial authority without changing unrelated behavior.

### Risk Domain

ADR-028 and ADR-031 assign financial projections, drawdown, exposure, margin,
portfolio heat, and deterministic authorization to Risk. `DeterministicRiskEngine`
is stateless over immutable snapshots. Its existing `ProjectionEngine` projects
an open proposed position and uses `expectedLossAtStop`; it does not yet model a
closed hypothetical Stop or Target outcome.

Existing reusable semantics include:

- `AccountSnapshot` with balance, equity, starting balance, daily baseline,
  daily closed P/L, currency, version, and capture time.
- `PortfolioSnapshot` and `ProjectedMetrics` for deterministic portfolio
  projections.
- `RiskHeadroomProjection` and Trading Core `RiskHeadroom` for daily and total
  headroom with unavailable states.
- versioned Risk rules, including `DAILY_DRAWDOWN` and
  `MAX_TOTAL_DRAWDOWN`.

The current `TradePlanRiskEvaluationService` assembles broker facts, risk-day
boundaries, market valuation, baselines, and Risk context, but its official and
preflight paths persist evaluations. The scenario preview must not call those
mutation-bearing command paths. A read-only context acquisition path or a pure
scenario projection boundary is required.

### Account and Dashboard

`AccountDashboardSummary` exposes balance, equity, daily P/L, drawdown,
valuation status, valuation timestamp, and valuation policy version. It is a
presentation read model, not sufficient by itself as the complete Risk input.
Risk-owned account facts must remain authoritative for scenario calculations.

### Challenge

`ChallengeProgressService` calculates current progress from the active Challenge
definition and account progression source, and exposes daily/total Risk headroom
from a completed monitoring evaluation. `ChallengeEvaluator` is mutation-capable
and can mark local satisfaction, provider confirmation, phase transitions, or
terminal status. It must not be called for a projection.

`ChallengeProgressionValueResolver` already distinguishes authoritative fresh
equity from realized/cash balance. A scenario projection needs a pure equivalent
over hypothetical account facts while preserving the definition's progression
source, phase target, Risk policy version, and minimum-trading-day semantics.

### Market Valuation and Currency

`MarketValuationPort` is the existing provider-neutral conversion boundary. It
returns timestamped facts, policy version, completeness, acquisition outcomes,
and freshness requirements. Risk currently fails closed on incomplete valuation
and currency mismatch. The projection must use this boundary for account-level
currency consequences and must not mix instrument, quote, account, or Challenge
currency without explicit conversion.

### Costs

Current PAPER settlement semantics document zero simulator fees. Broker facts
include fee, swap, and realized P/L fields, and Market Intelligence has a
historical fee schedule used by research readiness, but no general exact future
fee contract is currently part of persisted Trade Plan projection. V1 should be
explicitly `GROSS`, with fees, spread, slippage, swap, and financing excluded
unless exact authoritative data is demonstrated for the requested scenario.

## Architecture Decision

The smallest coherent architecture is an on-demand read projection owned by
Trading Core/Risk Domain, not Market Intelligence and not Angular:

```text
TradePlanRiskPort exact version
        + Trading Core authoritative account/broker facts
        + MarketValuationPort
        + Risk rules and Challenge definition
                    ↓
        pure deterministic scenario projection
                    ↓
             typed API response
                    ↓
             PlanPage presentation
```

The endpoint should be exposed through the existing authenticated Gateway /
Trading Core boundary after the exact API route is confirmed. A GET-like read
is appropriate only if all inputs come from the persisted Trade Plan and current
authoritative snapshots. No client-supplied mutable hypothetical body is needed
for the initial story.

## Lifecycle Position

The projection belongs after `PREPARE TRADE`, when exact Entry, Stop, Target,
quantity, account, and Trade Plan version are known. It should appear in
PlanPage before the human accepts or rejects the plan and independently of the
Risk evaluation command. It must not be inserted into Opportunity investigation
where exact plan parameters do not yet exist.

## Scenario Semantics

- `CURRENT`: current authoritative account and Challenge facts with observation
  time and freshness.
- `STOP`: hypothetical gross Entry-to-Stop consequence; unavailable without a
  valid Stop, quantity, entry semantics, currency conversion, account facts, or
  Risk context.
- `TARGET`: hypothetical gross Entry-to-first-Target consequence; unavailable
  without a valid target or the same required facts.
- `RISK`: positive gross monetary consequence of Entry-to-Stop.
- `REWARD`: positive gross monetary consequence of Entry-to-first-Target.
- `RISK_REWARD`: numeric Reward/Risk only when both are valid and Risk is
  positive. It has no probability or recommendation semantics.

LONG/SHORT geometry must reuse Trade Plan validation. Missing or contradictory
geometry fails closed. Multiple targets require an explicit V1 choice; the
existing Risk handoff exposes the first target, so V1 should project the first
target and disclose that basis.

## Risk and Challenge Integration

The implementation must preserve `INCLUSIVE`/`STRICT` rule boundaries and the
configured Risk-day reset instead of recalculating them. It must derive
hypothetical account facts through a pure Risk-owned function or immutable
scenario snapshot, then evaluate consequences without persistence.

Challenge progress may be calculated hypothetically from the existing
definition/progression source. It must never call `ChallengeEvaluator`, mark
`LOCALLY_SATISFIED`, confirm a provider, advance a phase, or add a trading day.
No Challenge remains a valid partial result: account and Risk projection may be
available while Challenge is `NOT_APPLICABLE`.

## Security and No Mutation

Authorization must verify the authenticated owner, Trade Plan owner/context,
account association, and exact Trade Plan version server-side. A plan id alone
must not disclose another user's account facts.

The projection must not create or update TradePlan versions, Risk evaluations,
Challenge instances, Account state, Trade, ExecutionIntent, ExecutionAttempt,
broker state, or accounting events. Tests must assert these invariants.

## Persistence and Provenance

Default is on-demand recomputation. Persistence is not justified by the current
read-only use case. The response should include Trade Plan id/version, observed
account facts timestamp/version, valuation timestamp/policy, Risk policy/rule
versions, Challenge definition/version when applicable, projection timestamp,
and `economicsBasis=GROSS`.

## ADR Assessment

No new ADR is required if the implementation extends the already accepted
ADR-028 financial authority with a read-only deterministic projection and does
not introduce a new authority, persisted hypothetical state, or cross-service
transaction. If implementation requires a new financial authority or changes
the meaning of `RiskValidationResult`, implementation must stop and request an
ADR decision.

## Risks and Open Decisions for the Implementation Plan

- The current Risk context acquisition is coupled to persistence-bearing
  evaluation flows; the pure read path must be designed before coding.
- Existing plan economics are gross and simple; provider-specific contract
  multipliers, fees, funding, spread, and slippage are not generally available.
- Challenge current progress is account-derived and may be unavailable when
  equity freshness or valuation status is not authoritative.
- The exact Gateway route and Trading Core controller boundary must be verified
  before adding the API.
- Multiple target allocation semantics need an explicit V1 decision; first
  target is the current Risk handoff's smallest consistent choice.

## Gate

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan is produced or any implementation file is changed.
