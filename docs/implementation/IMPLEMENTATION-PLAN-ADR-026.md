# IMPLEMENTATION-PLAN-ADR-026

- **Related ADR:** ADR-026 — Trading Opportunity Model
- **Status:** Implemented
- **Goal:** Incrementally implement the Trading Opportunity architecture while preserving the project's architectural principles.

---

# Implementation Strategy

Implementation follows the architectural layering defined in ADR-026.

Each task should be implemented independently.

No task should introduce functionality belonging to a later phase.

Every phase must compile and all tests must pass before moving to the next one.

---

# Phase 1 — Domain Model

## Task 1 — Opportunity Aggregate

### Objective

Create the TradingOpportunity aggregate root.

### Deliverables

- TradingOpportunity
- OpportunityId
- immutable aggregate structure
- constructor validation

### Acceptance Criteria

- aggregate is immutable
- unique identifier exists
- no persistence annotations outside project conventions
- unit tests

---

## Task 2 — Value Objects

### Objective

Implement all Opportunity value objects.

### Deliverables

- OpportunityScore
- OpportunityVersion
- OpportunityPriority (if required)
- supporting value objects

### Acceptance Criteria

- immutable
- equality implemented correctly
- validation included
- unit tests

---

## Task 3 — Enumerations

### Objective

Create all domain enumerations.

### Deliverables

- OpportunityStatus
- OpportunityOrigin
- OpportunityType (if applicable)

### Acceptance Criteria

- enums documented
- no business logic
- unit tests

---

## Task 4 — Aggregate Relationships

### Objective

Model aggregate relationships.

### Deliverables

References to:

- Observations
- AI Analyses

without introducing execution concepts.

### Acceptance Criteria

- aggregate remains persistence-safe
- no circular dependencies
- immutable references

---

# Phase 2 — Application Contracts and Creation

## Task 5 — Opportunity Repository Contract

### Objective

Define the domain-facing persistence contract required to store and retrieve Trading Opportunities.

The repository contract must remain independent from the persistence technology.

### Deliverables

- `TradingOpportunityRepository`
- query methods required by the Opportunity Engine and Registry
- support for retrieving:
    - an Opportunity by identifier and version
    - the latest version of an Opportunity
    - active Opportunities
    - logically equivalent Opportunities used for deduplication
    - Opportunity history

### Constraints

- the repository interface belongs to the domain or application boundary according to existing project conventions;
- no JPA-specific type may leak into the contract;
- repository operations must preserve immutable versions;
- saving a new version must never overwrite an older version;
- user-specific queries do not belong to this repository.

### Acceptance Criteria

- persistence technology is not exposed;
- history can be queried;
- the current version can be resolved;
- deduplication queries are supported;
- repository contract tests or architecture tests validate package boundaries.

---

## Task 6 — Opportunity Creation Command

### Objective

Introduce an explicit input model for requesting Opportunity creation.

The Opportunity Engine must not accept an unstructured collection of arbitrary values.

### Deliverables

- `CreateOpportunityCommand`
- required identifiers and contextual data
- validation of mandatory inputs
- optional AI-analysis references
- explicit `OpportunityOrigin`

### Expected Information

The command should contain or reference:

- instrument
- direction
- scenario
- timeframe
- supporting Observation identifiers
- AI Analysis identifiers, when available
- origin
- evaluation timestamp
- validity information, when already known

### Constraints

- the command is not a domain entity;
- the command must not contain persistence objects;
- the command must not contain execution parameters;
- Observation references are mandatory;
- AI Analysis references are optional;
- user-specific preferences are forbidden.

### Acceptance Criteria

- invalid commands are rejected before aggregate creation;
- at least one Observation is required;
- no Entry, Stop Loss, Take Profit or Position Size is present;
- tests cover mandatory and optional fields.

---

## Task 7 — Opportunity Builder

### Objective

Implement the internal component responsible for constructing valid immutable TradingOpportunity instances.

The Builder creates the aggregate from already prepared and validated knowledge.

### Deliverables

- `OpportunityBuilder`
- aggregate construction logic
- initial version assignment
- initial lifecycle assignment
- traceability metadata creation
- invariant validation

### Responsibilities

The Builder:

- creates a new Opportunity identifier when required;
- assigns the initial version;
- assigns `DETECTED` as the initial status;
- attaches Observation references;
- attaches optional AI Analysis references;
- assigns origin;
- assigns the initial OpportunityScore supplied by the appropriate policy;
- validates aggregate invariants.

### Non-responsibilities

The Builder does not:

- query repositories;
- detect duplicates;
- analyze market data;
- execute AI;
- calculate ranking;
- manage expiration;
- transition existing Opportunities;
- access user-specific context.

### Constraints

- the Builder must not be exposed through a REST controller;
- only the Opportunity Engine may invoke it;
- construction must be deterministic for identical prepared inputs, except for generated identifiers and timestamps supplied through explicit abstractions;
- direct public aggregate constructors should be restricted according to project conventions.

### Acceptance Criteria

- valid aggregates can be created;
- creation without Observation references fails;
- the initial lifecycle is `DETECTED`;
- the initial version is valid;
- AI references remain optional;
- unit tests cover every invariant.

---

## Task 8 — Fusion Policy Contracts

### Objective

Define the policy contracts used by the Opportunity Engine to transform collected intelligence into Opportunity creation inputs.

Fusion policies must remain replaceable and independently testable.

### Deliverables

Policy interfaces for:

- Opportunity scenario resolution
- Opportunity type resolution, when retained by the domain model
- OpportunityScore calculation
- initial validity calculation
- supporting evidence selection

Possible names:

- `OpportunityFusionPolicy`
- `OpportunityScoringPolicy`
- `OpportunityValidityPolicy`
- `OpportunityEvidencePolicy`

The exact decomposition may follow existing project conventions, provided responsibilities remain separated.

### Responsibilities

Fusion policies may:

- evaluate deterministic Observations;
- consider AI Analyses;
- resolve a coherent scenario;
- calculate a business-prioritization score;
- determine which evidence supports the Opportunity;
- propose an initial validity window.

### Non-responsibilities

Fusion policies do not:

- persist Opportunities;
- manage lifecycle transitions;
- execute external AI models;
- access broker APIs;
- rank Opportunities for a specific user;
- create Trade Plans.

### Constraints

- deterministic and AI-derived inputs must remain distinguishable;
- OpportunityScore must not be represented as win probability;
- policies must be stateless where possible;
- policy selection must be extensible without modifying the aggregate.

### Acceptance Criteria

- policy contracts are documented;
- default deterministic implementations exist where required for compilation;
- AI-derived inputs remain explicitly identifiable;
- unit tests cover policy behavior;
- architecture tests prevent infrastructure dependencies.

---

## Task 9 — Deduplication Policy

### Objective

Implement the deterministic policy used to identify logically equivalent Opportunities.

### Deliverables

- `OpportunityDeduplicationPolicy`
- logical identity representation
- configurable equivalence time window
- comparison result describing:
    - new Opportunity
    - equivalent Opportunity
    - possible replacement or version candidate

### Logical Identity Inputs

The policy should consider:

- instrument
- direction
- scenario
- timeframe
- supporting Observations
- temporal proximity

The exact algorithm may evolve, but its contract must not depend on persistence implementation details.

### Constraints

- deduplication must be deterministic;
- two database rows are not automatically two logical Opportunities;
- equivalent situations must produce a new version of the same logical Opportunity;
- materially different scenarios must remain separate Opportunities;
- the policy must not mutate existing objects.

### Acceptance Criteria

- identical logical inputs are considered equivalent;
- different instruments are not equivalent;
- different directions are not equivalent;
- different scenarios can remain independent;
- time-window boundaries are tested;
- Observation-set comparison is tested.

---

## Task 10 — Opportunity Engine

### Objective

Implement the application service that exclusively orchestrates Trading Opportunity creation and reevaluation.

The Opportunity Engine is the only authorized entry point for creating new TradingOpportunity aggregates.

### Deliverables

- `OpportunityEngine`
- creation use case
- reevaluation use case
- orchestration of:
    - Observation retrieval
    - AI Analysis retrieval
    - fusion policies
    - deduplication
    - OpportunityBuilder
    - repository persistence
- explicit result model

Possible result types:

- `OpportunityCreated`
- `OpportunityVersionCreated`
- `OpportunityRejected`
- `NoOpportunityDetected`

The exact representation may follow existing application-result conventions.

### Creation Flow

```text
CreateOpportunityCommand
          │
          ▼
Validate command
          │
          ▼
Load Observations
          │
          ▼
Load optional AI Analyses
          │
          ▼
Apply fusion policies
          │
          ▼
Evaluate logical duplication
          │
     ┌────┴─────┐
     ▼          ▼
New logical   Existing logical
Opportunity   Opportunity
     │          │
     ▼          ▼
Version 1     Next version
     └────┬─────┘
          ▼
Opportunity Builder
          │
          ▼
Persist immutable version
          │
          ▼
Return creation result
```

### Responsibilities

The Opportunity Engine:

- validates orchestration prerequisites;
- gathers trusted knowledge;
- applies policies;
- resolves whether to create a new logical Opportunity or a new version;
- invokes the Builder;
- persists the result;
- emits application or domain events when required by project conventions.

### Non-responsibilities

The Opportunity Engine never:

- computes technical indicators;
- creates Observations;
- executes AI models directly;
- places broker orders;
- calculates position size;
- stores user preferences;
- ranks Opportunities for individual users.

### Constraints

- no other service may create TradingOpportunity instances;
- the Engine must depend on abstractions;
- creation and persistence must be transactionally consistent;
- immutable historical versions must never be overwritten;
- AI absence must not prevent deterministic Opportunity creation;
- invalid or missing Observations must prevent creation;
- clocks and identifier generation should be injectable abstractions where the project already follows this pattern.

### Acceptance Criteria

- a valid command creates an Opportunity;
- equivalent input creates a new version instead of a duplicate logical Opportunity;
- invalid Observation references fail safely;
- optional AI Analysis absence is supported;
- persistence occurs once per successful creation;
- older versions remain intact;
- unit tests cover orchestration branches;
- integration tests cover repository interaction;
- ArchUnit tests ensure only the Engine invokes the Builder.

---

# Phase 3 — Lifecycle and Registry Foundations

## Task 11 — Lifecycle Transition Policy

### Objective

Define deterministic rules governing Opportunity lifecycle transitions.

### Deliverables

- `OpportunityLifecyclePolicy`
- allowed transition rules
- transition validation result
- lifecycle-related domain exceptions or result types

### Allowed Transitions

Initial transitions should support:

```text
DETECTED → ANALYZED
ANALYZED → ACTIVE
ACTIVE → CONSUMED
ACTIVE → EXPIRED
DETECTED → EXPIRED
ANALYZED → EXPIRED
```

Additional transitions must not be introduced without explicit business justification.

### Constraints

- lifecycle transitions create a new immutable Opportunity version;
- terminal states must not transition back to active states;
- AI cannot directly authorize a transition;
- transition rules are deterministic;
- lifecycle policy does not persist aggregates.

### Acceptance Criteria

- valid transitions succeed;
- invalid transitions are rejected;
- terminal-state behavior is tested;
- every accepted transition produces a new version;
- existing versions remain unchanged.

---

## Task 12 — Opportunity Registry Contract

### Objective

Define the application-facing contract for managing and querying the currently relevant Opportunity set.

### Deliverables

- `OpportunityRegistry`
- active-Opportunity registration
- current-version resolution
- lifecycle transition operations
- active Opportunity queries
- history access delegation
- deduplication lookup delegation

### Responsibilities

The Registry:

- exposes the current version of each logical Opportunity;
- exposes active Opportunities;
- coordinates lifecycle updates;
- resolves historical versions through the repository;
- supports consumers such as dashboards and scanners.

### Non-responsibilities

The Registry does not:

- create Opportunities;
- calculate OpportunityScore;
- execute fusion policies;
- analyze markets;
- personalize results;
- create Trade Plans.

### Constraints

- the Registry must use the Opportunity Engine for creation and reevaluation;
- lifecycle changes must use deterministic lifecycle policies;
- every state transition must produce a new immutable version;
- Registry queries must not expose persistence entities.

### Acceptance Criteria

- current versions are returned correctly;
- historical versions remain accessible;
- active queries exclude consumed and expired Opportunities;
- lifecycle changes create new versions;
- Registry cannot directly invoke the Builder;
- tests cover current-version and historical behavior.


# Phase 4 — Registry, Ranking and User Projections

## Task 13 — Opportunity Registry Implementation

### Objective

Implement the concrete application service behind the Opportunity Registry contract.

The Registry becomes the central access point for active Trading Opportunities.

### Deliverables

- `DefaultOpportunityRegistry`
- active Opportunity index
- current version resolution
- history delegation
- lifecycle delegation
- registry query API

### Responsibilities

The Registry shall:

- expose active Opportunities
- resolve the latest version
- expose historical versions
- expose Opportunity lookups
- delegate lifecycle transitions
- coordinate with the Opportunity Repository

### Non-responsibilities

The Registry shall never:

- create Opportunities
- analyze markets
- calculate scores
- execute AI
- personalize results

### Acceptance Criteria

- active Opportunity retrieval works
- historical retrieval works
- latest version retrieval works
- lifecycle delegation works
- integration tests completed

---

# Task 14 — Expiration Policy

### Objective

Implement deterministic expiration policies.

### Deliverables

- `OpportunityExpirationPolicy`
- expiration evaluator
- expiration reasons
- expiration decision object

### Responsibilities

Evaluate whether an Opportunity should expire based on:

- validity window
- market changes
- Observation invalidation
- scenario invalidation
- market session
- configurable business rules

### Constraints

AI may recommend expiration.

Only deterministic policies may expire an Opportunity.

### Acceptance Criteria

- expiration rules configurable
- deterministic evaluation
- unit tests
- integration tests

---

# Task 15 — Opportunity Ranking Engine

### Objective

Implement Opportunity prioritization.

### Deliverables

- `OpportunityRankingEngine`
- ranking strategies
- sorting policies
- filtering

### Responsibilities

Produce ordered Opportunity lists for:

- dashboards
- scanners
- notifications

### Constraints

Ranking must never modify Opportunities.

Ranking must not depend on persistence implementation.

### Acceptance Criteria

- deterministic ranking
- stable sorting
- configurable strategies
- unit tests

---

# Task 16 — User Opportunity Projection

### Objective

Implement user-specific Opportunity projections.

### Deliverables

- `UserOpportunity`
- projection repository
- projection service

### User Data

Examples:

- favorite
- hidden
- notification status
- read status
- custom priority
- personal notes

### Constraints

TradingOpportunity must never contain these fields.

### Acceptance Criteria

- multiple users share one Opportunity
- personalization isolated
- integration tests

---

# Phase 5 — Persistence

## Task 17 — Persistence Model

### Objective

Implement Opportunity persistence.

### Deliverables

Persistence entities

Repository implementation

Mapping

Version persistence

History persistence

### Constraints

Persistence must preserve:

- immutability
- history
- version chain

No update-in-place.

### Acceptance Criteria

- history stored
- current version query
- version chain maintained
- integration tests

---

## Task 18 — Mapping Layer

### Objective

Implement mapping between persistence and domain.

### Deliverables

- mappers
- conversion logic

### Constraints

No business logic inside mappers.

### Acceptance Criteria

- mapping coverage
- unit tests

---

# Phase 6 — REST API

## Task 19 — Opportunity REST API

### Objective

Expose Opportunities through REST.

### Deliverables

Controller

DTOs

Mapping

Endpoints

### Suggested Endpoints

GET

/opportunities

GET

/opportunities/{id}

GET

/opportunities/active

GET

/opportunities/history/{id}

### Constraints

Controllers return:

ResponseEntity

No persistence objects exposed.

### Acceptance Criteria

- endpoint tests
- DTO validation
- OpenAPI updated

---

## Task 20 — Search API

### Objective

Expose Opportunity search capabilities.

Examples:

- by instrument
- by timeframe
- by status
- by type
- active only

### Acceptance Criteria

- filtering works
- pagination supported
- sorting supported

---

# Phase 7 — Testing

## Task 21 — Unit Tests

Cover:

- Opportunity aggregate
- Builder
- Engine
- Registry
- Ranking
- Lifecycle
- Expiration
- Deduplication

Coverage target:

> 90%+

---

## Task 22 — Integration Tests

Validate:

Repository

Persistence

REST

Transactions

Versioning

History

Lifecycle

---

## Task 23 — Architecture Tests

Verify:

- only Opportunity Engine creates Opportunities
- Builder not publicly accessible
- Registry cannot create Opportunities
- no infrastructure dependency in domain
- immutable aggregate
- no user fields inside TradingOpportunity
- no execution fields inside TradingOpportunity

---

# Phase 8 — Documentation

## Task 24 — Documentation

Update:

Architecture diagrams

Package documentation

JavaDoc

Developer documentation

---

## Task 25 — Final Review

Checklist

- ADR-026 respected
- all tasks completed
- tests passing
- documentation updated
- architecture review completed

---

# Implementation Order

```text
1. Domain Model
        │
        ▼
2. Repository Contract
        │
        ▼
3. Opportunity Builder
        │
        ▼
4. Fusion Policies
        │
        ▼
5. Opportunity Engine
        │
        ▼
6. Lifecycle
        │
        ▼
7. Registry
        │
        ▼
8. Ranking
        │
        ▼
9. User Projection
        │
        ▼
10. Persistence
        │
        ▼
11. REST API
        │
        ▼
12. Tests
        │
        ▼
13. Documentation
```

# Definition of Done

The implementation is complete when:

- every ADR-026 decision is implemented;
- all aggregate invariants are enforced;
- immutable versioning is operational;
- deterministic lifecycle management is implemented;
- the Opportunity Engine is the sole creation entry point;
- Registry and Ranking Engine are operational;
- user projections are fully isolated;
- REST APIs expose the expected functionality;
- unit, integration and architecture tests pass successfully;
- documentation reflects the implemented architecture.
