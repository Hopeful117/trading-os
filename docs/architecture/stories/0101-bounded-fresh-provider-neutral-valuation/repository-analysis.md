# Story 0101 - Repository Analysis

## Scope

This analysis covers the provider-neutral fresh valuation path used by Market
Data and consumed by Trading Core for PRE_TRADE/T0 and execution-time T1
decisions. It includes the bounded acquisition lifecycle, structured outcomes,
provider-neutral request propagation, and the runtime PAPER acceptance boundary.

## Relevant Architecture

ADR-055 assigns provider acquisition, source timestamps, normalization,
valuation freshness, provenance, deadlines, and temporary subscription cleanup
to Market Data. Trading Core supplies the decision-boundary requirement and
must not select REST, WebSocket, provider symbols, or provider payloads. Risk
remains deterministic, read-only, provider-neutral, and fail-closed.

The affected runtime path is:

```text
Trading Core T0/T1 valuation request
  -> provider-neutral MarketValuationPort
  -> Market Data internal valuation endpoint
  -> cached observation reuse when valid
  -> bounded REST-first acquisition when required
  -> bounded temporary ticker subscription when supported
  -> normalized PriceObservation persistence
  -> valuation freshness and completeness classification
  -> structured acquisition outcome
  -> Risk fail-closed decision when not FRESH
```

## Repository Evidence

- `market-data/.../MarketPriceSnapshotService` owns bounded acquisition,
  deadline handling, source-time validation, coalescing, and diagnostics.
- `market-data/.../MarketSubscriptionService` exposes a bounded subscription
  boundary and releases temporary subscriptions on exit.
- `market-data/.../ValuationSnapshotService` returns valuation data together
  with acquisition outcomes.
- `trading-core/.../MarketValuationPort` carries provider-neutral valuation
  requirements and outcomes.
- `trading-core/.../MarketValuationClient` transports the contract without
  exposing provider-specific fields.
- `TradePlanRiskEvaluationService` uses explicit T0 requirements.
- `ExecutionTimeRiskRevalidationService` independently requests the stricter
  T1 `CONFIRM_OR_ACQUIRE` requirement.

## Risks Identified

- A successful provider response can still contain an old source timestamp and
  must remain `STALE_PROVIDER` rather than becoming fresh through `capturedAt`.
- Timeout and interruption paths must not leave provider futures or temporary
  subscriptions active.
- T0 and T1 must not silently reuse an observation that does not satisfy their
  respective requirements.
- Runtime acceptance must prove both execution-time T1 and the post-settlement
  outbox path without introducing live broker execution.

## Analysis Conclusion

The implementation scope is coherent with ADR-055. The repository contains the
required changes and focused tests. Runtime evidence is available for T0, T1,
PAPER entry, close, Challenge state, and both outbox deliveries. Separate
mark-to-market observation while a position remained open was not captured;
that remains an explicit validation gap rather than an inferred pass.

## Workflow Note

This artifact was generated after implementation from current repository,
test, database, and runtime evidence. It records the engineering context; it
does not infer or grant a Human Approval Gate.

## DevLog Context

DevLog context retrieval was attempted for Story 0101 but was unavailable due
to exhausted provider quota (`429 insufficient_quota`). The repository and ADR
were therefore used as the authoritative sources.
