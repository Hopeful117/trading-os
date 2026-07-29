# ADR-020 — V1 implementation foundation

## Scope

The first ADR-020 increment introduces a dedicated Java orchestration boundary
in `market-intelligence`. It is a stateless read-only service. It does not own
market, account, risk, news or broker data and it does not execute orders.

The service is intentionally modular so its domain and application contracts
can evolve independently from Spring, Feign and future AI providers.

## Placement decision

The orchestration does not belong in:

- Market Data, because that service owns facts and objective market metrics;
- Trading Core, because that service owns user business state and risk rather
  than opportunity analysis;
- AI Engine, because deterministic capabilities must remain independent and
  first class.

The V1 therefore uses a dedicated `market-intelligence` service:

```text
Gateway
   |
   v
Market Intelligence
   |-- Market Data (facts and normalized history)
   |-- News Service (future contributor)
   |-- Trading Core (future authenticated private contributor)
   `-- AI Engine (future probabilistic capability adapter)
```

The current service uses synchronous REST/OpenFeign calls through Eureka. No
message broker, distributed cache or additional database is introduced.

## Data flow

```text
Analysis request
      |
      v
Passive or Active strategy
      |
      v
Context requirements
      |
      v
Context contributors --> modular IntelligenceContext
      |                           |
      |             +-------------+-------------+
      |             |                           |
      v             v                           v
execution      deterministic capabilities   AI capabilities
control             |                           |
      +-------------+-------------+-------------+
                                  |
                                  v
                     ConsolidatedIntelligence
```

Deterministic and AI capabilities receive their context independently. A
capability receives only the sections declared by its own requirements.
Deterministic findings are never converted into AI findings, and AI findings
are never exposed as factual observations.

## Context model

`IntelligenceContext` is an immutable collection of modular `ContextSection`
instances. Each section contains:

- a section type;
- `AVAILABLE`, `STALE`, `MISSING` or `UNAVAILABLE`;
- `PUBLIC` or `USER_PRIVATE` sensitivity;
- a typed payload;
- source and timestamps;
- an explicit diagnostic message when incomplete.

Implemented contributors:

- market identity;
- current normalized market snapshot;
- historical OHLC for active analysis.

Defined extension points:

- order flow;
- news and economic calendar;
- account and risk context;
- user objectives;
- previous intelligence.

## Passive mode

Passive mode is bounded to two capabilities and a 750 ms per-capability
timeout. It requests only market identity and the current market snapshot.

The initial deterministic spread capability runs in passive mode. The AI
capability participates in selection but is reported as unavailable until an
AI Engine adapter exists. No fabricated AI result is emitted.

Scheduling, incremental persistence and subscription ownership are deferred.

## Active mode

Active mode has a broader capability budget and a 3 second per-capability
timeout. It requests:

- market identity and current snapshot;
- OHLC history;
- optional order-flow and news sections.

Missing optional contributors produce an explicit partial result. Missing
required context or stale market data produces a degraded result.

## Result and traceability

`ConsolidatedIntelligence` exposes:

- context section statuses without leaking their complete internal payloads;
- factual, deterministic and future AI findings as separate typed results;
- capability identity and origin;
- context sections used by each finding;
- confidence where applicable;
- capability completion, failure, timeout or unavailability;
- execution mode, budget, duration and timestamps;
- overall `COMPLETE`, `PARTIAL`, `DEGRADED` or `FAILED` status.

No global opaque score is produced.

## REST contract

```text
POST /api/v1/intelligence/analyses
```

Example request:

```json
{
  "marketId": "00000000-0000-0000-0000-000000000000",
  "mode": "ACTIVE",
  "objective": "Understand current liquidity and market conditions"
}
```

The Gateway protects the route with the existing JWT policy. The current
request contains only a public `marketId`; no account context is loaded.

## Current limitations

- no AI Engine implementation;
- no News Service;
- no account/risk contributor;
- no order-book REST snapshot contributor;
- no passive scheduler or persisted intelligence repository;
- no cache or reuse of previous analyses;
- one deterministic spread capability only;
- no Angular Scanner or Market Intelligence view.

## Extension rules

- AI Engine integration implements `AiEnginePort`; it must return explicitly
  AI-originated findings and cannot write authoritative state.
- News integration adds a `ContextContributor` without moving news ownership.
- Account/risk integration requires an authenticated request and a dedicated
  Trading Core contract before any private section is loaded.
- Passive persistence requires a separate retention and ownership decision.
- An event-driven execution model, if needed, requires a separate ADR.
