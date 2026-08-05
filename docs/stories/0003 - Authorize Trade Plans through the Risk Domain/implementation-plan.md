# Implementation Plan

## Story

Story 0003 — Authorize Trade Plans through the Risk Domain.

## Approved Architecture

Story 0003 establishes the deterministic authorization boundary between Trade
Planning and Broker Execution. A Trade Plan produced by Market Intelligence must
pass through a deterministic authorization workflow and be transformed into an
immutable, traceable and idempotent authorization result before it becomes
eligible for execution. No broker execution is performed by this Story.

```text
Market Intelligence
    Trade Plan (immutable, versioned, ACCEPTED)
        |
        v
Trading Core
    Orchestration entry point
        |
        v
    RiskEvaluationContext assembly
    (account, portfolio, market, rule-set snapshots)
        |
        v
Risk Domain
    Deterministic Risk Evaluation
        |
        v
    RiskEvaluation (immutable)
        |
        v
Trading Core
    Persistence (immutable, idempotent, traced)
        |
        v
    REST response -> exposed through the Gateway
        |
        v
Execution (future Story, out of scope)
```

The implementation must preserve the separation introduced by ADR-028 and
clarified by ADR-031. The single source-of-truth responsibility matrix is
defined in the Responsibilities section.

Trading Core orchestrates; it never performs deterministic risk evaluation.
Risk Domain evaluates; it never orchestrates, persists, or accesses external
services. Broker Service provides facts only. Market Intelligence produces
Trade Plans only.

The authorization modules must not gain any coupling to the Execution Domain
defined by ADR-029. This guarantees that Story 0003 completes a self-contained,
verifiable authorization stage that the future Execution story can consume.

## Existing Baseline and Scope of the Delta

Story 0001 and Story 0002 already provide the production authorization machinery
inside Trading Core. This plan builds on that baseline rather than recreating it.

### Existing Baseline

- `TradePlanRiskEvaluationService` orchestrates evaluation, assembles the
  `RiskEvaluationContext` through `RiskEvaluationContextBuilder` and invokes the
  Risk Domain `RiskEngine`.
- `RiskEvaluationModels.Command` / `.Response` / `.Reason` / `.Trace` define the
  idempotent command and the immutable response contract.
- `RiskPersistence` persists evaluations, context snapshots, component
  snapshots, risk-day baselines and the acknowledgment outbox.
- The public REST endpoint
  `POST /api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations`
  exists, requires an `Idempotency-Key`, and derives the actor from the
  authenticated `UserDto` principal.
- Existing tests cover orchestration, fail-closed behaviour, idempotency and
  acknowledgment delivery.

### Genuine Delta for Story 0003

- Verify and harden the authorization orchestration so it is a first-class,
  provable end-to-end behavior of Trading Core.
- Expose the public authorization endpoint through the Gateway so it is
  reachable by authenticated Trading Core clients.
- Add targeted tests for the authorization flow, the Gateway route, and the
  architectural boundary with the Execution Domain.

### Scope of Module Changes

#### Trading Core — mandatory

- keep the authorization orchestration entry point idempotent and immutable;
- keep context assembly fail-closed for every required authoritative snapshot;
- keep persistence immutable and fully traced to the evaluated Trade Plan;
- do not couple authorization code to the Execution Domain.

#### gateway — mandatory (additive)

- add a route forwarding `POST /api/v1/trade-plans/**` to `lb://trading-core`;
- keep JWT validation intact;
- do not alter any other route.

#### risk-domain — no production change

- Risk Domain remains the deterministic authority and is not modified by this story. It is exercised through the existing Trading Core integration.

No other backend module and no Angular source is in scope.

## Responsibilities

One source-of-truth responsibility matrix. Each component owns its boundary and
never performs another component's work.

| Component | Owns | Never does |
|-----------|------|------------|
| Market Intelligence | analysis, observations, opportunities, Trade Plans | financial validation, risk decisions |
| Trading Core | orchestration, context assembly, persistence, API exposure | deterministic risk evaluation |
| Risk Domain | evaluation, rules, authorization decision | orchestration, persistence, external I/O |
| Broker Service | external financial facts | authorization, business decisions |
| Market Data Service | market prices and market reference data | financial validation |

### Authority Boundaries

Maintaining the architecture requires respecting the following authority
boundaries at every step.

| Authority | Holder |
|-----------|--------|
| Trade Planning | Market Intelligence |
| Context Assembly | Trading Core |
| Deterministic Authorization | Risk Domain |
| Broker Facts | Broker Service |
| Market Prices | Market Data Service |

Story 0003 must reinforce these boundaries rather than introducing additional
coupling between services. In particular, Trading Core must never evaluate risk
itself, and the Risk Domain must never be bypassed.

### Mutability Rules

Every business object crossing the authorization pipeline is immutable. A change
creates a new version; existing objects are never modified.

- Trade Plans.
- Snapshots (account, portfolio, market, rule-set).
- RiskEvaluationContext.
- RiskEvaluation.

## Current Business Flow

The current deterministic workflow relevant to this Story is represented by the
authorization stage of the full decision pipeline:

```text
Trade Plan
    |
    v
RiskEvaluationContext assembly (Trading Core)
    |
    v
Deterministic Risk Evaluation (Risk Domain)
    |
    v
RiskEvaluation (immutable, persisted)
```

Execution is intentionally outside the scope of this Story.

## Contract Design

### 1. Authorization orchestration

Trading Core's orchestration entry point is `TradePlanRiskEvaluationService`.
It must retain the following responsibilities.

Inputs:

- authenticated actor ID taken from the JWT `UserDto` principal;
- the exact Trade Plan identity and version;
- the trading account ID;
- an `Idempotency-Key`;
- the command requested-at timestamp.

Responsibilities:

1. resolve the idempotent stored evaluation for the actor and key;
2. return the stored response on exact replay, rejecting conflicting binds;
3. load the account and verify the actor owns it;
4. resolve the account risk configuration, the broker-account mapping and the
   effective assigned risk profile;
5. assemble the authoritative `RiskEvaluationContext` from snapshots;
6. invoke the Risk Domain `RiskEngine` exactly once;
7. persist the immutable evaluation with full provenance;
8. record the acknowledgment outbox only for an approved decision;
9. return the immutable response.

The orchestration must never perform risk calculations itself and must never
invoke the Execution Domain.

### 2. Risk Evaluation Context assembly

Context assembly is the responsibility of Trading Core. All snapshots must
originate from authoritative sources and be bound to a coherent observation
instant.

Required snapshots per ADR-028:

- `AccountSnapshot` — account identity/version, balance, equity, margin,
  daily-start balance, daily closed PnL and capture time;
- `PortfolioSnapshot` — portfolio identity/version, positions (with protection
  and conservative price provenance) and capture time;
- `MarketSnapshot` — market/valuation facts bound to instrument and asset
  prices with provenance and the observed time;
- `RuleSetSnapshot` — the exact effective risk rule set resolved from the
  assigned profile and policies.

Missing, stale or internally inconsistent snapshots must fail the evaluation
closed. Trading Core must never infer balances, margin, leverage, buying power,
rule versions or exposure from other values.

### 3. Risk Domain invocation

The Risk Domain is the only authority that produces the deterministic decision.

- Trading Core builds an immutable `RiskEvaluationContext` and a
  `RiskEvaluationRequest`;
- the Risk Domain `RiskEngine` evaluates the request and returns an immutable
  `RiskValidationResult`;
- Trading Core maps that result into the public immutable response and never
  re-interprets or overrides the decision.

The Risk Domain never queries repositories, calls external services or loads
additional information. Everything it needs already exists inside the context.

### 4. Persistence

The authorization result must be persisted immutably and remain traceable to:

- the evaluated Trade Plan identity and version;
- the account;
- the snapshots used and their source versions;
- the rule set / policy versions;
- the evaluation timestamp and engine version;
- the deterministic decision.

Persistence records include the evaluation record, the context snapshot, the
component snapshots (account, portfolio, market, rule set), the risk-day
baseline when required, and the acknowledgment outbox. No persisted object is
updated in place; a new evaluation always creates new immutable records.

### 5. Idempotency

Evaluation is idempotent at the application boundary.

- The `Idempotency-Key` is mandatory and scoped by actor.
- An exact replay with the same actor, key and command identity returns the
  already-persisted response without re-evaluation or side effects.
- A replay whose command identity differs under the same key must fail with a
  deterministic conflict outcome.
- Approved evaluations also enqueue an acknowledgment that is delivered
  durably and retried; a retry of the same key must not re-evaluate and must
  still trigger delivery of the pending acknowledgment.

Concurrent identical requests must converge on one stored evaluation and one
logical outcome.

### 6. Response contract

The public response is immutable and structured:

- evaluation ID;
- Trade Plan identity and version;
- account ID;
- evaluation status (`COMPLETED`, `CONTEXT_UNAVAILABLE`, ...);
- deterministic decision (`APPROVED`, `APPROVED_WITH_WARNINGS`, `REJECTED` or
  absent when the evaluation did not complete);
- approved flag;
- structured reasons (violations) and warnings;
- global metrics;
- evaluated-at timestamp;
- trace (correlation ID, engine version, policy/rule versions, snapshot
  versions).

### 7. Controlled outcomes

The workflow must distinguish:

- a completed authorization with a decision;
- a rejected authorization with structured reasons;
- an unavailable/unknown authorization produced by a fail-closed outcome
  (missing configuration, profile, snapshots, margin, prices, or a dependency
  failure).

No authorization may be granted when required information is incomplete. The
workflow never fails open. An ambiguous external outcome after an order request
is out of scope of this Story (no order is requested) but the same principle
keeps evaluation conservative under uncertainty.

## Persistence Plan

Use existing repository ports as application boundaries. The persistence layer
already exists and is considered stable; Story 0003 does not introduce new
tables.

Tables already in use and preserved:

- `risk_evaluation` — immutable evaluation records keyed by evaluation ID with
  an idempotency key bound to actor;
- `risk_context_snapshot` — one immutable context snapshot per evaluation;
- `risk_component_snapshot` — immutable component snapshots per evaluation;
- `risk_day_baseline` — immutable risk-day baseline per account/day;
- `risk_acknowledgment_outbox` — durable acknowledgment delivery with claim
  leases and retry scheduling;
- `account_risk_configuration` — authoritative account risk configuration;
- `risk_profile` / `risk_profile_rule` — immutable rule profiles;
- `account_risk_profile_assignment` — effective profile assignment.

No schema change is required for Story 0003. If a schema change becomes strictly
necessary during implementation, it must be additive, schema-only, and migrate
no fabricated data. It must preserve every existing Story 0001 table and row.

## Transaction and Concurrency Rules

- Persist the official evaluation, context and component snapshots atomically in
  one transaction.
- Persist the acknowledgment outbox atomically with an approved evaluation.
- Deliver the acknowledgment after commit with durable retry and an idempotent
  downstream endpoint.
- A retried idempotent evaluation must inspect and retry incomplete
  acknowledgment delivery rather than returning immediately with a stale result.
- Concurrent identical requests must converge on one stored evaluation and one
  logical outcome.
- A failed remote call must leave a retryable state, not a false success.
- The orchestration transaction must not roll back an already-persisted
  evaluation simply because the subsequent remote acknowledgment failed.

## Controlled Error Contract

Use stable codes and HTTP semantics:

- `IDEMPOTENCY_CONFLICT` — 409;
- `TRADE_PLAN_VERSION_MISMATCH` — 409;
- `TRADE_PLAN_NOT_ACCEPTED` — 422;
- `TRADE_PLAN_FORBIDDEN` — 403;
- `ACCOUNT_NOT_FOUND` — 404;
- `ACCOUNT_FORBIDDEN` — 403;
- fail-closed context outcomes (`CONTEXT_UNAVAILABLE` with a structured reason)
  — returned as an unavailable evaluation, not as a generic server failure;
- `AUTHENTICATION_REQUIRED` — 401;
- `INVALID_COMMAND` — 400;
- `DEPENDENCY_UNAVAILABLE` — 503 semantics without unintended downstream side
  effects.

Do not expose provider payloads, stack traces, secrets, or broker-specific
values.

## Implementation Phases

### Phase 1 — Verify and harden orchestration

- confirm `TradePlanRiskEvaluationService` preserves idempotency and immutable
  evaluation;
- confirm all required context snapshots fail closed when unavailable;
- confirm the Risk Domain is the only decision authority;
- add or retain focused unit tests proving orchestration, context assembly,
  deterministic invocation, persistence and idempotency.

Exit criteria: an immutable Trade Plan can be transformed into an immutable,
idempotent, persisted evaluation without any Execution coupling.

### Phase 2 — Gateway exposure

- add a Gateway route forwarding `POST /api/v1/trade-plans/**` to
  `lb://trading-core`;
- keep JWT validation and all existing routes intact;
- add a Gateway route test proving the public authorization endpoint is
  reachable through the route.

Exit criteria: the public authorization endpoint is reachable through the
Gateway for authenticated clients.

### Phase 3 — Validation and cleanup

- run all affected module suites independently;
- run the Gateway route test and the architectural boundary test;
- verify `git diff --check`;
- document exact results and any remaining issues in
  `implementation-report.md`.

Exit criteria: focused tests pass, no authorization module couples to
Execution, and no unrelated behavior is changed.

## Test Plan

### Trading Core focused tests

- authorization orchestration produces an immutable evaluation;
- context assembly uses authoritative snapshots and fails closed when any is
  missing or invalid;
- the Risk Domain is the sole decision authority and is invoked exactly once;
- evaluations are persisted immutably and traceable to the exact Trade Plan
  version;
- missing/invalid Trade Plan, missing configuration, missing profile and
  unavailable required margin fail closed;
- idempotent replay returns the stored response without re-evaluation;
- conflicting idempotency binds are rejected;
- approved evaluation enqueues and delivers the acknowledgment; no
  acknowledgment on rejection or fail-closed outcome;
- local commit failure never triggers remote acknowledgment;
- no authorization code references the Execution Domain package.

### Gateway focused tests

- the `/api/v1/trade-plans/**` route forwards to `lb://trading-core`;
- existing routes remain intact;
- JWT validation remains configured for protected routes.

### risk-domain tests

- existing deterministic engine and policy tests remain green; no new risk
  production code is introduced by this Story.

### Full validation commands

Run each Maven service independently because the repository has no root
aggregator:

```text
cd risk-domain && mvn test
cd trading-core && ./mvnw test
cd gateway && mvn test     # when gateway is modified (Spring WebFlux)
git diff --check
```

Never report a suite as passing unless it completed successfully. Record the
exact command and result in the implementation report.

## Acceptance-Criteria Trace

| Story criterion | Planned proof |
| --- | --- |
| Trade Plans can be evaluated through the Risk Domain | orchestration service integration test through the Risk Engine |
| Trading Core assembles RiskEvaluationContext from authoritative snapshots | context-assembly and fail-closed tests |
| Risk evaluations are immutable and persisted | persistence and replay tests |
| Authorization decisions are deterministic | Risk Domain invocation test, same inputs same output |
| Authorization supports APPROVED / APPROVED_WITH_WARNINGS / REJECTED | decision-mapping tests |
| Unavailable/unknown outcomes are distinct and conservative | fail-closed `CONTEXT_UNAVAILABLE` tests |
| Evaluation is idempotent | replay and conflict tests |
| Traceability from Trade Plan to RiskEvaluation preserved | provenance and snapshot-version assertions |
| Relevant tests pass | affected module suites green |
| No unrelated behavior is changed | `git diff --check`, module-scope review |

## Constraints for the Implementation Engineer

- Treat the approved Story, Repository Analysis, this plan, the accepted ADRs
  (ADR-028, ADR-029, ADR-031) and the current repository state as authoritative.
- Preserve all pre-existing user changes, including IDE files. Do not discard or
  include unrelated edits.
- Do not commit, push, merge, reset, or rewrite history.
- Do not add a dependency unless it is already available in the affected
  module's managed Spring stack and strictly required.
- Do not duplicate Risk Domain rules in Trading Core.
- Do not introduce placeholder identities, synthetic versions, inferred balances
  or financial facts, or fail-open outcomes.
- Do not call Broker Service from Market Intelligence.
- Do not relax JWT validation when exposing the Gateway route.
- Do not modify Angular, Broker Service, Market Data, Market Intelligence, or
  Risk Domain production code unless a required change is explicitly identified.
- Stop and report if an accepted ADR contradicts a required contract rather than
  silently selecting a new architecture.
- Produce
  `docs/stories/0003 - Authorize Trade Plans through the Risk Domain/implementation-report.md`
  with modified files, migrations, tests, results and remaining issues.

## Human Review Checkpoints After Implementation

The engineer should inspect in IntelliJ:

- the public authorization endpoint is traceable and idempotent;
- context assembly cannot be bypassed with inferred financial facts;
- the Risk Domain is never overridden or replaced by Trading Core logic;
- persistence retains exact immutable versions and provenance;
- the acknowledgment outbox delivers approved evaluations durably;
- the Gateway route preserves JWT validation and existing routes;
- no authorization module references the Execution Domain package;
- no risk, Gateway, or Trading Core change enters unrelated modules.

## Approval

Implementation Plan approved by the human engineer on 2026-08-05.

Implementation delegation authorized.