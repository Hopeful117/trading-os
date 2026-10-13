# Story 0090 - Active PAPER Challenge Monitoring

## Metadata

- **ID:** `0090`
- **Status:** `IMPLEMENTATION COMPLETE - AWAITING HUMAN REVIEW`
- **Follows:** `0079`, `0086`, `0087`, `0088`, `0089`
- **Related ADRs:** `ADR-028`, `ADR-051`, `ADR-052`
- **Repository Analysis:** `repository-analysis.md`

## Goal

Introduce trigger-independent orchestration for actively reevaluating generic
ACTIVE PAPER Challenges while open positions and market prices change. The V1
trigger is a configurable periodic scheduler. The orchestration must reuse the
existing valuation, Risk, Challenge, settlement, and provider-neutral layers.

## Scope

- Discover ACTIVE Challenges in bounded, stable-ordered pages.
- Monitor only eligible PAPER accounts and isolate failures per Challenge.
- Reload authoritative state inside a transaction before reevaluation.
- Reuse `ChallengeReevaluationService`, `AccountRiskMonitoringService`,
  `RiskFactsProvider`, and the existing PAPER valuation path.
- Add a thin scheduler adapter with configurable enabled state, interval, and
  batch size.
- Add structured monitoring logs without exposing credentials or full account
  payloads.
- Cover discovery, terminal exclusion, failure isolation, open-position breach
  and target progression, unchanged repeated monitoring, concurrency, Risk Day,
  and disabled scheduler behavior.

## Architectural Constraints

- No provider, plan, capital, reset-time, or benchmark-specific branches.
- No duplicated equity, P&L, drawdown, target, or Challenge transition logic.
- No direct Market Data access from the scheduler or monitoring orchestrator.
- No event bus, Kafka, RabbitMQ, Redis Streams, WebSocket trigger, or execution
  action.
- LIVE accounts must never be monitored by this PAPER trigger.
- One monitored Challenge failure must not roll back other Challenges in the
  same cycle.
- Existing pessimistic Challenge locking, optimistic lifecycle versioning, and
  `REQUIRES_NEW` reevaluation boundaries must remain authoritative.

## Risk Day Decision Gate

Periodic monitoring must not claim exact reset-boundary baseline fidelity. The
repository currently captures the first authoritative balance observed inside a
Risk Day. Story 0090 may add generic boundary-aware triggering only if it can do
so without historical reconstruction or a new architectural capability.
Otherwise it must document the limitation explicitly and preserve the existing
first-observation semantics.

## Acceptance Criteria

- ACTIVE PAPER Challenges are discovered in bounded batches; PASSED and BREACHED
  Challenges are not monitored.
- Monitoring reloads authoritative Challenge and Account state and uses the
  existing deterministic reevaluation pipeline.
- PAPER CASH open-position market movement can transition ACTIVE to BREACHED
  without settlement or manual action.
- Fresh open-position equity reaching the configured target can transition ACTIVE
  to PASSED without closing the position.
- Market-data, valuation, or Risk failures leave the Challenge unchanged and do
  not abort other candidates.
- Repeated unchanged monitoring is safe and terminal Challenges are not retried.
- Concurrent monitoring and settlement do not corrupt lifecycle or baseline
  state.
- Scheduler configuration supports enabled state, interval, and batch size;
  disabled scheduling does not prevent application startup or direct use of the
  orchestration service.
- Monitoring remains provider-neutral and performs no automatic execution,
  position closure, broker disablement, or terminal provider action.
- Risk Day semantics are deterministic and the exact-boundary fidelity answer is
  explicitly reported as YES or NO.

## Out of Scope

- Provider-specific monitoring or Kraken-specific rules.
- Event-driven market-data infrastructure and distributed scheduling.
- Adaptive polling, frontend/dashboard work, execution, funding, fees, leverage,
  margin, liquidation, payout lifecycle, or migration repair unrelated to this
  Story.
