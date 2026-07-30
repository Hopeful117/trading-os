# IMPLEMENTATION-PLAN-ADR-027

- **Related ADR:** ADR-027 — Trade Planning Model
- **Status:** Implemented
- **Goal:** Incrementally implement the Trade Planning architecture while preserving the architectural principles defined by ADR-027.

---

# Implementation Strategy

Trade Planning is the bridge between market intelligence and execution.

Implementation must strictly respect the separation between:

- Trading Opportunity
- Trade Plan
- Risk Engine
- Execution Engine

Each phase must compile successfully and all tests must pass before continuing.

---

# Phase 1 — Domain Model

## Task 1 — TradePlan Aggregate

### Objective

Implement the TradePlan aggregate root.

### Deliverables

- `TradePlan`
- `TradePlanId`
- immutable aggregate
- constructor validation

### Constraints

The aggregate shall:

- be immutable;
- own a unique identifier;
- own a lifecycle;
- own a version;
- reference one or more Trading Opportunities;
- reference exactly one Trading Context;
- remain independent from broker implementations.

### Acceptance Criteria

- immutable aggregate;
- constructor validation;
- aggregate invariants enforced;
- unit tests.

---

## Task 2 — Value Objects

### Objective

Implement every TradePlan value object.

### Deliverables

Examples include:

- `TradePlanVersion`
- `EntryStrategy`
- `StopLoss`
- `TakeProfit`
- `PositionSizing`
- `RiskReward`
- `ExpirationPolicy`

Additional value objects may be introduced when they improve domain clarity.

### Constraints

Every value object shall:

- be immutable;
- validate itself;
- correctly implement equality.

### Acceptance Criteria

- validation completed;
- equality tested;
- immutability preserved.

---

## Task 3 — Enumerations

### Objective

Implement domain enumerations.

### Deliverables

Examples include:

- `TradePlanStatus`
- `EntryType`
- `TradeDirection`

Additional enums may be introduced if required by the domain.

### Acceptance Criteria

- no business logic;
- documented values;
- unit tests.

---

## Task 4 — Aggregate Composition

### Objective

Implement the internal composition of the TradePlan aggregate.

### The aggregate shall contain

Execution information:

- entry strategy;
- stop loss;
- take profit;
- position sizing;
- expiration.

Strategic information:

- originating Opportunities;
- trading thesis;
- supporting Observations;
- supporting AI Analyses;
- confirmation conditions;
- invalidation conditions.

### Constraints

Execution details and strategic reasoning must remain clearly separated.

TradePlan shall never contain:

- broker orders;
- execution reports;
- positions;
- realized PnL.

### Acceptance Criteria

- clear aggregate composition;
- immutable references;
- domain invariants validated.

---

# Phase 2 — Lifecycle

## Task 5 — Lifecycle Model

### Objective

Implement the TradePlan lifecycle.

### Deliverables

Lifecycle support for:

- DRAFT
- PROPOSED
- ACCEPTED
- REJECTED
- EXPIRED
- RISK_VALIDATED
- READY_TO_EXECUTE
- EXECUTED

### Constraints

Lifecycle transitions shall be deterministic.

Every transition creating a meaningful business change shall create a new immutable TradePlan version.

### Acceptance Criteria

- valid transitions implemented;
- invalid transitions rejected;
- lifecycle unit tests completed.

---

## Task 6 — Versioning

### Objective

Implement immutable versioning.

### Deliverables

Support for:

- version creation;
- current version resolution;
- historical versions;
- version references.

### Constraints

Older versions must never be modified.

Every new TradePlan version shall preserve complete historical traceability.

### Acceptance Criteria

- immutable history;
- version chain maintained;
- integration tests.

---

# End of Part 1


# Phase 3 — Trading Context

## Task 7 — TradingContext Model

### Objective

Introduce the TradingContext model required to generate a TradePlan.

### Deliverables

Implement a domain model capable of representing the execution context used by the Trade Planning Engine.

The initial model should support references to:

* trading account;
* account currency;
* available capital;
* available buying power;
* leverage;
* risk profile;
* applicable rule profile;
* existing market exposure;
* execution preferences.

### Constraints

TradingContext shall:

* be immutable;
* represent a snapshot of the context used during planning;
* remain independent from broker-specific API models;
* expose only information required by Trade Planning;
* reference stable domain identifiers rather than infrastructure entities when possible.

### Acceptance Criteria

* immutable TradingContext implemented;
* required fields validated;
* broker independence preserved;
* unit tests cover invalid and valid construction.

---

## Task 8 — TradingContext Snapshot Semantics

### Objective

Ensure that every TradePlan references the exact TradingContext snapshot used during its creation.

### Deliverables

Implement:

* `TradingContextId` or equivalent immutable identifier;
* context snapshot timestamp;
* optional context version;
* reference from TradePlan to the exact planning context.

### Constraints

A later account or risk-profile modification must not alter the historical context associated with an existing TradePlan.

The system must be able to explain which account conditions were used to calculate:

* position size;
* expected monetary risk;
* leverage requirements;
* exposure constraints.

### Acceptance Criteria

* historical context remains reproducible;
* existing TradePlans are unaffected by later context changes;
* tests verify snapshot isolation.

---

# Phase 4 — Trade Plan Construction

## Task 9 — TradePlan Draft Model

### Objective

Introduce an intermediate planning representation used before an immutable TradePlan is created.

### Deliverables

Implement a mutable or incrementally composable internal model such as:

* `TradePlanDraft`;
* `TradePlanCandidate`;
* or an equivalent application-layer structure.

The representation may contain:

* selected Opportunities;
* execution parameters under construction;
* rationale under construction;
* planning warnings;
* incomplete planning results.

### Constraints

The draft model is not a persisted domain aggregate.

It must not be exposed as a completed TradePlan.

It must never bypass final domain validation.

### Acceptance Criteria

* incomplete plans can be assembled safely;
* invalid drafts cannot become TradePlans;
* the draft remains internal to the planning process;
* tests cover incomplete and valid drafts.

---

## Task 10 — TradePlan Builder

### Objective

Implement the unique component responsible for creating valid immutable TradePlan instances from completed planning drafts.

### Deliverables

Implement:

* `TradePlanBuilder`;
* final invariant validation;
* initial version assignment;
* initial lifecycle assignment;
* Opportunity-reference validation;
* TradingContext-reference validation.

### Constraints

The builder shall reject any plan missing mandatory information.

At minimum, it shall verify:

* at least one originating Trading Opportunity;
* one valid Trading Context;
* one supported instrument;
* one trade direction;
* one valid entry strategy;
* one valid risk definition;
* expiration or validity semantics;
* coherent confirmation and invalidation rules.

The builder shall not:

* validate broker availability;
* perform Risk Engine validation;
* execute orders;
* persist the TradePlan directly.

### Acceptance Criteria

* valid drafts generate immutable TradePlans;
* incomplete drafts are rejected;
* contradictory execution parameters are rejected;
* all invariants are unit tested.

---

# Phase 5 — Planning Policies

## Task 11 — PlanningPolicy Contract

### Objective

Define the extension contract used by deterministic planning policies.

### Deliverables

Implement an interface similar in responsibility to:

```java
public interface PlanningPolicy {

    boolean supports(PlanningInput input);

    PlanningContribution evaluate(PlanningInput input);
}
```

Exact method names and return types may follow repository conventions.

### PlanningInput

The input should provide only the information required by policies, including:

* Trading Opportunities;
* Trading Context;
* relevant market snapshot;
* existing planning contributions;
* planning request metadata.

### PlanningContribution

A contribution may propose:

* entry parameters;
* stop-loss placement;
* target placement;
* position-sizing constraints;
* expiration;
* confirmation rules;
* invalidation rules;
* management rules;
* rationale fragments;
* warnings.

### Constraints

Planning policies shall:

* be deterministic when given identical inputs;
* remain stateless unless explicitly justified;
* avoid persistence;
* avoid broker communication;
* avoid lifecycle transitions;
* never create TradePlans directly.

### Acceptance Criteria

* policy contract implemented;
* policy input and contribution models implemented;
* deterministic behaviour tested;
* unsupported inputs handled explicitly.

---

## Task 12 — Initial Deterministic Policies

### Objective

Implement a minimal set of planning policies sufficient to generate a coherent first TradePlan.

### Suggested Initial Policies

* entry-selection policy;
* stop-loss policy;
* target-selection policy;
* position-sizing input policy;
* expiration policy;
* confirmation-rule policy;
* invalidation-rule policy.

### Scope Guidance

The first implementation should remain conservative.

Policies may initially rely on already available Opportunity data and explicit configuration rather than advanced strategy logic.

The goal is to validate the architecture, not to implement every trading strategy.

### Constraints

Each policy shall have one focused responsibility.

A policy shall not silently override another policy.

Conflicts must be surfaced to the orchestrator.

### Acceptance Criteria

* each policy is independently testable;
* conflicting contributions are detectable;
* incomplete policy output prevents final plan creation;
* deterministic tests cover representative scenarios.

---

## Task 13 — Planning Policy Registry

### Objective

Implement discovery and ordered execution of applicable planning policies.

### Deliverables

Implement a component responsible for:

* registering policies;
* selecting supported policies;
* ordering policy execution;
* preventing accidental duplicate application;
* exposing the active policy set for observability.

### Constraints

Policy ordering must be explicit and deterministic.

Spring injection order must not become an undocumented business rule.

The registry must not perform planning itself.

### Acceptance Criteria

* deterministic policy order;
* duplicate policy detection;
* supported-policy filtering;
* registry unit tests.

---

# Phase 6 — AI Planning Integration

## Task 14 — AI Planning Port

### Objective

Define an optional application port through which the Trade Planning Engine may request structured AI planning contributions.

### Deliverables

Implement an interface representing AI-assisted planning.

The port may accept:

* selected Opportunities;
* Trading Context;
* deterministic planning results;
* market thesis;
* supporting Observations and AI Analyses.

It may return structured proposals for:

* strategic thesis;
* execution alternatives;
* confirmation rules;
* invalidation rules;
* position-management suggestions;
* explanation.

### Constraints

AI output shall:

* remain optional;
* be structured;
* never create or persist a TradePlan;
* never bypass deterministic validation;
* never perform risk authorization;
* never override deterministic risk controls.

Invalid or unavailable AI output must not make the core planning process unstable.

### Acceptance Criteria

* AI planning port implemented;
* no-op or disabled implementation available;
* malformed AI output is rejected safely;
* deterministic planning works without AI.

---

## Task 15 — AI Contribution Validation

### Objective

Validate AI-generated planning contributions before they enter the TradePlan draft.

### Deliverables

Implement validation for:

* supported instrument;
* coherent direction;
* valid prices;
* valid percentage and quantity ranges;
* supported management-rule types;
* traceable rationale;
* source references.

### Constraints

AI-generated values must be treated as untrusted input.

AI content must never directly mutate trusted project state or persisted domain knowledge.

The validation layer must convert accepted AI output into trusted internal planning contributions.

### Acceptance Criteria

* invalid AI proposals are rejected;
* partial AI failure does not corrupt deterministic results;
* validation failures are observable;
* tests cover malformed and contradictory proposals.

---

# Phase 7 — Trade Planning Engine

## Task 16 — Planning Request Model

### Objective

Implement the application command used to request TradePlan generation.

### Deliverables

Introduce a request model containing:

* one or more Trading Opportunity identifiers;
* Trading Context identifier;
* requesting user or actor;
* optional planning preferences;
* optional predecessor TradePlan version;
* optional reason for replanning.

### Constraints

The request must not contain trusted domain objects supplied directly by the client.

Referenced data must be resolved by application services or repositories.

### Acceptance Criteria

* required identifiers validated;
* duplicate Opportunity references rejected or normalized;
* unauthorized context access prevented;
* unit tests completed.

---

## Task 17 — TradePlanningEngine Orchestrator

### Objective

Implement the single orchestration entry point responsible for generating TradePlans.

### Responsibilities

The engine shall:

1. validate the planning request;
2. retrieve Trading Opportunities;
3. retrieve the Trading Context snapshot;
4. validate Opportunity compatibility;
5. initialize a TradePlan draft;
6. execute deterministic planning policies;
7. detect contribution conflicts;
8. optionally invoke AI planning;
9. validate AI contributions;
10. assemble the completed draft;
11. invoke the TradePlan Builder;
12. return the generated TradePlan.

### Non-responsibilities

The engine shall not:

* detect Trading Opportunities;
* validate final account risk;
* execute broker orders;
* manage positions;
* modify existing TradePlans;
* silently persist partial drafts.

### Acceptance Criteria

* TradePlanningEngine is the only public creation path;
* orchestration order is deterministic;
* failures stop creation cleanly;
* successful planning returns a valid immutable TradePlan;
* integration tests cover the full planning flow.

---

## Task 18 — Planning Conflict Resolution

### Objective

Introduce explicit handling for contradictory planning contributions.

### Examples

* two different entry prices;
* incompatible stop-loss methods;
* contradictory trade directions;
* incompatible expiration rules;
* conflicting position-management rules.

### Deliverables

Implement:

* conflict model;
* conflict type;
* source identification;
* resolution status;
* planning failure or explicit resolution policy.

### Constraints

The engine must never resolve material conflicts silently.

The initial implementation may reject conflicted planning requests.

Future ADRs may define ranking or arbitration mechanisms.

### Acceptance Criteria

* material conflicts are detected;
* conflict sources are identifiable;
* rejected planning attempts are observable;
* tests cover multiple conflict scenarios.

---

## Task 19 — Planning Result

### Objective

Return a structured result from the Trade Planning Engine.

### Deliverables

The result should distinguish between:

* successful TradePlan creation;
* insufficient planning data;
* incompatible Opportunities;
* policy conflict;
* AI contribution rejection;
* invalid Trading Context;
* domain invariant failure.

### Constraints

Application errors must not leak infrastructure implementation details.

The result should support both REST exposure and internal event-driven use.

### Acceptance Criteria

* explicit success and failure semantics;
* failure reasons are testable;
* no generic null-based result handling;
* result mapping remains independent from HTTP.

---

# End of Part 2

The final part will cover:

* replanning and version chains;
* persistence;
* application services;
* REST endpoints;
* risk-validation integration boundaries;
* domain events;
* observability;
* testing strategy;
* implementation order;
* final acceptance checklist.

# Phase 8 — Replanning

## Task 20 — Replanning Service

### Objective

Implement the application service responsible for generating new TradePlan versions when planning conditions change.

### Deliverables

Implement a service capable of:

* loading the current TradePlan;
* retrieving the latest Trading Opportunities;
* retrieving a fresh TradingContext;
* creating a new planning request;
* generating a new TradePlan version.

### Constraints

The service shall never modify an existing TradePlan.

Replanning always creates a new immutable version.

### Acceptance Criteria

* original TradePlan unchanged;
* new version created correctly;
* version lineage preserved;
* integration tests completed.

---

## Task 21 — Version Lineage

### Objective

Maintain explicit relationships between TradePlan versions.

### Deliverables

Support:

* previous version reference;
* next version discovery;
* latest version resolution;
* complete version history.

### Constraints

History must remain complete and immutable.

Removing intermediate versions is not supported.

### Acceptance Criteria

* version chain preserved;
* navigation tested;
* history reproducible.

---

# Phase 9 — Persistence

## Task 22 — Repository

### Objective

Persist TradePlan aggregates.

### Deliverables

Implement:

* TradePlanRepository;
* aggregate retrieval;
* version retrieval;
* persistence mapping.

### Constraints

Repositories expose domain concepts only.

Infrastructure details remain hidden.

### Acceptance Criteria

* save and retrieve TradePlans;
* version history persisted;
* integration tests completed.

---

## Task 23 — Persistence Mapping

### Objective

Define persistence mapping for the Trade Planning domain.

### Deliverables

Persist:

* TradePlan;
* lifecycle;
* version;
* TradingContext reference;
* Opportunity references;
* execution parameters;
* strategic rationale.

### Constraints

Persistence schema shall preserve immutability semantics.

Historical versions must remain queryable.

### Acceptance Criteria

* complete mapping implemented;
* historical queries validated;
* migration scripts prepared if required.

---

# Phase 10 — Application Layer

## Task 24 — Application Service

### Objective

Expose Trade Planning capabilities to the application layer.

### Responsibilities

The service may expose operations such as:

* create TradePlan;
* retrieve TradePlan;
* retrieve latest version;
* list versions;
* request replanning.

### Constraints

Business rules remain inside the domain.

The application service orchestrates only.

### Acceptance Criteria

* application layer isolated;
* orchestration tested;
* no duplicated business logic.

---

## Task 25 — REST Controllers

### Objective

Expose Trade Planning through REST APIs.

### Suggested Endpoints

```text
POST   /trade-plans

GET    /trade-plans/{id}

GET    /trade-plans/{id}/versions

POST   /trade-plans/{id}/replan
```

### Constraints

Controllers shall:

* use ResponseEntity;
* validate requests;
* return DTOs only;
* never expose domain entities directly.

### Acceptance Criteria

* endpoints documented;
* validation implemented;
* integration tests completed.

---

# Phase 11 — Integration Boundaries

## Task 26 — Risk Engine Integration

### Objective

Prepare the integration point with ADR-028.

### Deliverables

Expose an application boundary allowing the Risk Engine to validate a TradePlan.

The planning module shall not perform risk validation itself.

### Constraints

Trade Planning remains completely independent from risk authorization.

The integration shall validate an immutable TradePlan version.

### Acceptance Criteria

* integration boundary implemented;
* no circular dependency;
* architecture verified.

---

## Task 27 — Execution Boundary

### Objective

Prepare the future Execution Engine integration.

### Deliverables

Expose the validated TradePlan required for execution.

Execution components decide how to transform it into broker orders.

### Constraints

Trade Planning never communicates directly with brokers.

Execution remains the responsibility of ADR-029.

### Acceptance Criteria

* execution boundary documented;
* no broker dependency introduced.

---

# Phase 12 — Domain Events

## Task 28 — Domain Events

### Objective

Publish significant Trade Planning events.

### Initial Events

Examples include:

* TradePlanCreated
* TradePlanAccepted
* TradePlanRejected
* TradePlanExpired
* TradePlanVersionCreated
* TradePlanReadyForRiskValidation

### Constraints

Events represent completed business facts.

Events shall not contain infrastructure concerns.

### Acceptance Criteria

* domain events implemented;
* event payloads documented;
* publication tested.

---

# Phase 13 — Observability

## Task 29 — Logging and Metrics

### Objective

Provide operational visibility into Trade Planning.

### Suggested Metrics

* plans created;
* accepted plans;
* rejected plans;
* expired plans;
* replanning frequency;
* planning duration;
* policy conflicts;
* AI contribution failures.

### Acceptance Criteria

* metrics exposed;
* structured logging implemented;
* operational dashboard ready.

---

# Phase 14 — Testing

## Task 30 — Testing Strategy

### Objective

Validate the complete Trade Planning architecture.

### Unit Tests

Cover:

* aggregate invariants;
* value objects;
* lifecycle;
* versioning;
* builder;
* policies;
* TradePlanningEngine;
* conflict detection.

### Integration Tests

Cover:

* complete planning workflow;
* persistence;
* REST APIs;
* replanning;
* Risk Engine boundary.

### Acceptance Criteria

* high coverage of business rules;
* deterministic tests;
* reproducible scenarios.

---

# Recommended Implementation Order

1. TradePlan aggregate
2. Value Objects
3. Lifecycle
4. Versioning
5. TradingContext
6. TradePlan Draft
7. TradePlan Builder
8. PlanningPolicy contract
9. Initial planning policies
10. Policy Registry
11. AI Planning Port
12. AI Contribution Validation
13. TradePlanningEngine
14. Conflict Resolution
15. Replanning
16. Repository
17. Application Service
18. REST Controllers
19. Domain Events
20. Observability
21. Complete test suite

---

# Final Acceptance Checklist

The implementation is considered complete when:

* TradePlan is an immutable aggregate.
* Trade Planning is the only strategy creation layer.
* TradePlanningEngine is the exclusive orchestration entry point.
* TradingContext snapshots are preserved.
* Planning policies are deterministic and independently testable.
* AI contributions are validated before use.
* TradePlan version history is immutable.
* Replanning creates new versions only.
* Persistence preserves historical integrity.
* REST APIs expose planning capabilities without leaking domain entities.
* Integration boundaries with Risk Engine and Execution Engine are established.
* Domain events are published for significant planning milestones.
* All business rules are validated by automated tests.

---

# Next ADR

After completing this implementation plan, the next architectural milestone is:

**ADR-028 — Risk Engine**

The Risk Engine will consume immutable TradePlans and determine whether a specific version is authorized for execution according to account constraints, user-defined rules, and prop-firm requirements.
