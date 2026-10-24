# Story 0160 - Deterministic Trade Scenario Projection

## Metadata

**ID:** `0160`
**Title:** Deterministic Trade Scenario Projection
**Status:** CLOSED - HUMAN ACCEPTED

## Goal

Give the trader a deterministic, read-only comparison of the current account
state and the consequences of a persisted Trade Plan reaching its Stop or first
Target, including gross monetary Risk, Reward, and Risk/Reward.

## Scope

- Add an on-demand backend projection bound to an exact Trade Plan id/version.
- Expose `CURRENT`, `STOP`, and `TARGET` scenarios with truthful per-scenario
  availability and provenance.
- Reuse Trading Core account facts, Risk Domain boundary semantics, market
  valuation/currency conversion, and existing Challenge definitions.
- Include gross scenario economics explicitly; exclude fees, spread, slippage,
  swap, and financing unless authoritative exact facts are available.
- Expose deterministic account and Risk consequences where the existing
  snapshots and rule semantics support them.
- Expose Challenge progress consequences without persisting Challenge state,
  phase transitions, provider confirmation, or trading-day evidence.
- Add a read-only PlanPage presentation after the exact Trade Plan is loaded.
- Preserve the distinction between projection, Risk approval, acceptance, and
  execution.

## Out of Scope

- Probability, expected value, confidence, recommendations, or AI.
- New Opportunity ranking or scoring semantics.
- Automatic Risk approval, Trade Plan acceptance, or execution.
- Broker mutations, position monitoring, stop/target modification, or partial
  close behavior.
- Persisted projection state, projection tables, or accounting events.
- Net economics when fees or other execution costs are not authoritative.
- Provider confirmation, Challenge phase mutation, or minimum trading-day
  mutation.

## Acceptance Criteria

- [ ] A user authorized for a Trade Plan can request a projection for an exact
      Trade Plan version; another user's plan is rejected server-side.
- [ ] The response is typed and includes observation/projection timestamps,
      Trade Plan version, economics basis, availability, reasons, and provenance.
- [ ] `CURRENT` exposes only authoritative current account/Risk/Challenge facts.
- [ ] `STOP` computes the deterministic gross consequence when entry, stop,
      quantity, account facts, currency semantics, and Risk context are valid.
- [ ] `TARGET` computes the deterministic gross consequence when the first
      target and required facts are valid; missing target remains unavailable.
- [ ] Missing Stop, Target, quantity, account facts, conversion, or required
      Risk context fails closed without fabricated monetary values.
- [ ] Monetary Risk, Reward, and numeric Risk/Reward are backend-owned and do
      not imply probability or approval.
- [ ] Daily-loss and maximum-loss projections preserve existing boundary and
      Risk-day semantics.
- [ ] Challenge projection is read-only, does not advance phases, count trading
      days, or imply provider confirmation.
- [ ] Viewing or recomputing the projection does not mutate TradePlan, Risk,
      Challenge, Account, Trade, ExecutionIntent, ExecutionAttempt, or Broker
      state.
- [ ] Frontend renders available, partial, unavailable, and stale states and
      performs no financial calculations.
- [ ] Deterministic backend tests cover LONG/SHORT, missing inputs, geometry,
      zero Risk, currencies, Risk boundaries, Challenge/no-Challenge, ownership,
      and no-mutation behavior.
- [ ] Applicable frontend tests, production build, formatting, and runtime
      validation pass.

## Constraints

- ADR-028 remains the financial projection and Risk authority.
- Trading Core assembles authoritative financial context; Market Intelligence
  remains the Trade Plan authority.
- The projection is on-demand and recomputable, not persisted.
- The first validated economics basis is `GROSS` unless exact costs are proven.
- No frontend formula may replace backend financial semantics.

## Relevant ADRs

- `docs/architecture/adr/ADR-009.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-042.md`

## Relevant Modules

- `trading-core/src/main/java/com/hope/trading/trading_core/risk`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge`
- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/valuation`
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning`
- `risk-domain/src/main/java/com/hope/trading/risk`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/tradeplan`
- `trading-os-web/src/app/features/trade-planning/plan-page`

## Validation

- Risk Domain deterministic scenario tests.
- Trading Core ownership, currency, Risk boundary, Challenge, and no-mutation
  tests.
- Market Intelligence contract compatibility tests if the internal Trade Plan
  handoff changes.
- Full affected Maven suites.
- Angular tests and production build.
- Authenticated PAPER PlanPage runtime validation.

## Observed Gap

The validated flow remains distributed across the Opportunity, Trade Plan, and
Risk screens. The deterministic projection is available and the decision
boundaries are preserved, but the trader must still navigate between pages to
complete the full review. A future cockpit/Decision Workspace should converge
the relevant context, projection, Risk decision, and execution handoff into a
continuous workflow. This is an identified UX improvement and is not a failure
of Story 0160 acceptance criteria.

## Closure Record

Story 0160 was accepted after authenticated PAPER runtime validation. The
projection endpoint, deterministic Risk Domain calculations, ownership and
no-mutation boundaries, PlanPage presentation, frontend checks, production
build, and runtime proof were validated. The remaining multi-page cockpit gap
is recorded above as follow-up scope. The implementation commit is pending;
no commit, push, pull request, or merge was performed by the agent.
