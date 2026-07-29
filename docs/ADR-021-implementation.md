# ADR-021 — Execution governance foundation

## Ownership

Market Intelligence owns `AnalysisExecution`, context authorization,
orchestration policy, consolidation and the future promotion of findings into
`IntelligenceObservation`.

The future AI Engine owns only execution of authorized AI capabilities,
progress reporting and structured results. Its port receives an already
filtered `IntelligenceContext`. It has no contract allowing it to query Market
Data, Trading Core, Broker Service or News Service.

## Execution lifecycle

`AnalysisExecution` is an immutable domain aggregate. Every transition creates
a new instance and is validated against the lifecycle:

```text
REQUESTED -> ACCEPTED -> CONTEXT_BUILDING -> RUNNING
                                           |       |
                                           |       -> COMPLETED
                                           -> PARTIALLY_COMPLETED -> COMPLETED
```

`FAILED`, `CANCELLED` and `EXPIRED` are terminal alongside `COMPLETED`.
Late results cannot replace a result after a terminal transition. Accepted
results are held by the immutable aggregate.

The V1 dispatcher uses a local virtual-thread executor. `AnalysisExecution`
storage is behind `AnalysisExecutionRepository`; its current adapter is
in-memory and therefore not durable across restarts. A durable adapter can
replace it without changing the application or REST contracts.

## Policy

`AnalysisExecutionPolicy` is provider-independent and contains:

- total and per-capability duration;
- maximum AI requests and parallel capabilities;
- context limits and maximum classification;
- retry policy and retry classifications;
- capability priorities;
- explicit degradation permissions.

No provider, model, token or prompt-specific field is present.

## Context governance

Each `ContextContributor` exposes a `ContextContributionDescriptor` declaring:

- provided section types;
- `PUBLIC`, `INTERNAL`, `USER_CONFIDENTIAL`, `TRADING_SENSITIVE` or
  `RESTRICTED`;
- expected freshness;
- allowed consumer families.

`RESTRICTED` is never disclosable. The earlier `ContextSensitivity` remains
only as the compatibility projection of the ADR-020 result contract until that
wire model is versioned.

## AI Engine boundary

`AiEnginePort` is asynchronous and exposes submission, progress, result lookup
and cooperative cancellation. The disabled adapter advertises itself as
unavailable and never fabricates a result.

Security types (`ServiceIdentity`, `AuthorizedCapability`, `ExecutionTrace`)
carry the logical authorization and correlation contract. No OAuth, JWT
validation, user token forwarding or service credential mechanism is
implemented.

## REST API

All endpoints return `ResponseEntity`:

```text
POST /api/v1/intelligence/analyses
GET  /api/v1/intelligence/analyses/{executionId}
GET  /api/v1/intelligence/analyses/{executionId}/result
POST /api/v1/intelligence/analyses/{executionId}/cancel
```

Creation requires `Idempotency-Key`, returns `202 Accepted` and a `Location`
header. Result lookup returns `202 Accepted` while no accepted result exists.

## Observation separation

`AnalysisExecution` is technical history. `IntelligenceObservation` is
long-lived business knowledge referencing one or more source executions.
No automatic promotion or observation persistence is implemented in this
increment.

## Security assumptions

The Gateway retains its existing client-facing JWT policy. The new
service-to-service types are contracts only. The future transport must
authenticate `service:market-intelligence`, authorize individual operations
and must never forward user JWTs or broker secrets.

## Deferred

- durable execution and observation persistence;
- retry scheduler and targeted capability restart;
- remote AI Engine transport;
- progress callbacks or polling adapter;
- technical service authentication;
- observation promotion policy;
- cache and freshness strategy from ADR-022.
