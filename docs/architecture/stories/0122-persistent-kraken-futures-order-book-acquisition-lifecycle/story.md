# Story 0122 - Persistent Kraken Futures Order Book Acquisition Lifecycle

## Metadata

**ID:** `0122`
**Status:** Repository analysis complete - awaiting approval
**Related Stories:** `0112A`, `0119`, `0120`, `0121`
**Related ADRs:** `ADR-063`, `ADR-064`

## Goal

Restore the configured Kraken Futures Order Book acquisition after a normal
`market-data` application restart without requiring a manual subscription API
call.

The initial operational scope is:

```text
provider: KRAKEN
market: BTC/USD
product: LINEAR_PERPETUAL
provider symbol: PF_XBTUSD
depth: 25
```

## In Scope

- one authoritative, configuration-backed desired Order Book subscription source;
- startup discovery and restoration after application readiness;
- reuse of the existing subscription service and provider path;
- idempotent startup restoration and duplicate protection;
- fresh provider snapshot and new sequence semantics after restart;
- bounded startup diagnostics and failure handling;
- focused unit and integration tests;
- preservation of existing lifecycle diagnostics, evidence, replay, OHLC, fee,
  funding, FeatureSnapshot, and outcome behavior.

## Out of Scope

- automatic reconnect or retry after unexpected live-stream termination;
- checksum implementation or checksum trust changes from Story 0121;
- historical evidence rewriting or sequence resurrection;
- a generic subscription orchestration platform;
- database-backed user subscription intent;
- fee or funding capture changes;
- Market Intelligence, Trading Core, Broker Service, or economic-result changes.

## Acceptance Criteria

- A configured desired Order Book subscription is discovered after normal
  `market-data` startup.
- Startup uses the canonical subscription service and does not require a manual
  `POST /api/v1/markets/{marketId}/subscriptions` call.
- Repeated startup restoration and an equivalent REST subscription do not create
  duplicate provider streams.
- Provider connection failure leaves the service running in a truthful degraded
  state without fake active state, sequence, or evidence.
- A fresh provider snapshot creates a new sequence ID.
- The previous invalidated sequence remains immutable and is never reused.
- No delta is appended to an old sequence or accepted before a valid snapshot.
- Normal shutdown releases runtime resources without removing desired intent.
- No automatic reconnect/retry loop or checksum implementation is introduced.

## Decision Boundary

The selected V1 desired-state source and startup lifecycle require human approval
of the Repository Analysis and a separate approval of the Implementation Plan
before code changes begin.
