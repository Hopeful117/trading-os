# Implementation Plan - Story 0163

## Overview

Implement the first deterministic Market Thesis composition capability inside
`market-intelligence`.

The implementation will compose the existing Observation, Trend Context,
Market Structure, capability provenance, and context availability semantics into
an immutable market-scoped thesis. It will persist each thesis version, expose a
read-only authenticated contract, and preserve optional provenance into
Opportunity/TradePlan without changing their decision semantics.

The implementation will not add analyzers, AI, cTrader data, scoring, Risk
authorization, execution behavior, or frontend financial logic.

## Planned Changes

### 1. Define the thesis domain model

**Component:** `market-intelligence` domain.

Add a focused `MarketThesis` aggregate/value model with immutable identity,
lineage, version, market identity, scenario, state, timestamps, validity,
provenance, and input fingerprint.

Add thesis-specific value types for:

- analytical state: `NO_TRADE`, `WATCH`, `CANDIDATE`;
- evidence role: supporting, contradicting, missing;
- evidence availability: available, unavailable, stale, unknown,
  not-applicable, not-implemented;
- contradiction severity: informational, weakening, blocking;
- structured scenario direction and conditional statements;
- confirmation conditions and status;
- invalidation conditions;
- relevant level references;
- structured analytical reason codes.

The model must validate the key invariants at construction time:

- immutable version values are positive;
- evidence contributions retain source and timestamp provenance;
- required missing/stale evidence cannot be represented as support;
- blocking contradictions cannot produce `CANDIDATE`;
- thesis state cannot represent Risk approval or execution authorization;
- validity cannot end before it begins.

Human-readable explanation remains a projection of structured content and cannot
be the only representation of confirmation or invalidation.

### 2. Add the deterministic composition input boundary

**Components:** Market Intelligence application/domain services.

Add a composer input boundary that accepts existing authoritative references,
not provider payloads or unrestricted service access.

The input assembly will use:

- the current `AnalysisExecution` and cutoff;
- persisted Observation and ObservationEvidence references;
- Trend Context typed content, findings, contradictions, exclusions and
  invalidations when present;
- Market Structure typed results when present;
- context section status/provenance where the current execution exposes it;
- existing validity/freshness timestamps and input fingerprints.

The composer will not recalculate Trend, Structure, volatility, spread,
liquidity, Momentum, Volume, or News semantics. It will only classify existing
inputs into thesis contributions and evaluate the approved state predicates.

Optional contributors that are absent remain explicit missingness. No fallback
from Trend to Momentum/Volume or from provider volume to semantic volume is
allowed.

### 3. Compose and persist a thesis in the existing analysis pipeline

**Component:** `ProductionIntelligencePipeline` integration.

Compose the thesis after the required observation/context inputs are available
and before StrategyEvaluation creates downstream candidates.

The pipeline must persist a thesis even when no StrategyDefinition matches, so
`NO_TRADE`, `WATCH`, missing-data, and contradiction outcomes are retained.

The existing strategy loop remains semantically unchanged:

- governed/applicable strategies are selected as today;
- StrategyMatch remains the only authority for a strategy match;
- TradingOpportunity remains derived from StrategyMatch;
- Opportunity score and lifecycle are untouched.

If thesis composition is unavailable because a mandatory composition input
cannot be acquired, the pipeline must persist an explicit degraded thesis or
controlled analytical outcome according to the state policy; it must not invent
supporting evidence and must not silently bypass the thesis boundary.

### 4. Persist immutable thesis versions

**Components:** Market Intelligence persistence port and adapter.

Add a `MarketThesisRepository` and a JPA persistence representation following
the existing Observation persistence conventions.

Use one thesis metadata row plus a serialized immutable payload snapshot for the
initial implementation. Persist at least:

- thesis ID;
- lineage ID;
- version;
- market/instrument identity;
- state;
- scenario direction/key;
- evaluated/cutoff/validity timestamps;
- composer policy/version;
- input fingerprint;
- analysis execution ID where present;
- created timestamp;
- supersession reference where applicable;
- serialized structured scenario/evidence/missingness/provenance payload.

Add database constraints for lineage/version uniqueness and deterministic input
fingerprint idempotency where compatible with the existing migration strategy.

Raw Market Data provider payloads are not duplicated. Source references and
fingerprints are retained instead.

Reevaluation creates a new version or lineage and never updates historical
thesis content. Repository methods must support exact-version loading and
latest-valid thesis lookup without exposing mutable aggregate state.

### 5. Add optional downstream provenance references

**Components:** Market Intelligence Opportunity and TradePlan provenance.

Add an optional immutable `MarketThesisReference` containing thesis ID and
version where the current pipeline can carry it without changing existing
semantics.

The reference may be added to Opportunity provenance and copied into
`TradingRationale`/TradePlan provenance. It must:

- remain optional for legacy rows and manual TradePlans;
- preserve exact ID/version;
- never be inferred from instrument, direction, price or timestamp;
- never affect Opportunity score, StrategyMatch status, TradePlan lifecycle,
  Risk, or execution;
- remain immutable once included in a historical TradePlan version.

If a current persistence contract cannot be extended safely within Story scope,
the implementation must preserve the thesis read contract and stop for human
guidance rather than inventing a cross-domain join.

### 6. Expose an authenticated read contract

**Components:** Market Intelligence application/query/web layer.

Add a read-only contract under the existing versioned intelligence API
conventions. The minimum operations are:

- load an exact thesis by ID/version;
- load the latest valid thesis for a market and scenario identity where the
  query can remain unambiguous.

The response must expose structured state, scenario, supporting evidence,
contradictions, missingness, freshness, confirmation, invalidation, levels,
validity and provenance.

The controller must use `ResponseEntity`, require existing authenticated
service/user boundaries, and distinguish not found, stale, unavailable and
degraded outcomes without converting them into a negative market conclusion.

No frontend screen is added in this Story. The contract is a provider-neutral
read boundary for future human decision-support surfaces.

### 7. Add deterministic tests and persistence validation

Implement tests in dependency order:

1. domain construction and invariant tests;
2. composer state/evidence tests;
3. deterministic fingerprint/replay tests;
4. repository and migration tests;
5. pipeline persistence/idempotency tests;
6. Opportunity/TradePlan optional provenance compatibility tests;
7. authenticated read contract tests.

Tests must prove that no thesis path calls Risk or execution authorization and
that no thesis outcome is treated as a human decision.

## Files to Modify

Expected candidates, subject to exact repository conventions:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/ProductionIntelligencePipeline.java`
  - invoke deterministic thesis composition without changing strategy semantics.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/TradingOpportunity.java`
  - add optional thesis provenance only if required by the approved contract.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/tradeplan/TradingRationale.java`
  - preserve optional exact thesis reference if downstream provenance is included.
- existing Opportunity/TradePlan factories, mappers and persistence adapters
  - carry optional thesis reference without changing legacy rows or lifecycle.
- existing Market Intelligence web/configuration classes
  - register the read-only query and controller.

## Files to Create

Expected candidates:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesis.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisState.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisEvidence.java`
- thesis-specific value types for scenario, confirmation, invalidation, levels,
  missingness and contradiction severity.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposer.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisInputAssembler.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/MarketThesisRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaMarketThesisEntity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaMarketThesisRepository.java`
- `market-intelligence/src/main/resources/db/migration/*market_thesis*.sql`
- read/query/controller/response types under the existing Market Intelligence
  web/application conventions.
- focused domain, composer, persistence, pipeline, contract, and invariant
  tests.

Exact names may be refined during implementation, but no new generic evidence
platform or service may be introduced.

## Dependencies

### Internal dependencies

- existing Observation repository and rehydration conventions;
- existing capability/artifact provenance;
- Trend Context and Market Structure typed contracts;
- `AnalysisExecution` cutoff and provenance;
- existing Market Intelligence authentication and API conventions;
- existing Opportunity and TradePlan persistence where optional provenance is
  carried;
- current Flyway migration and JPA configuration.

### External dependencies

None.

No new Maven, npm, broker, cTrader, AI or provider dependency is authorized.

### Ordering dependencies

1. Add and test domain value objects and invariants.
2. Add input assembly and deterministic composer.
3. Add persistence and immutable version/idempotency behavior.
4. Integrate composition into the production pipeline.
5. Add optional downstream provenance only if current contracts support it
   without semantic changes.
6. Add authenticated read contract.
7. Execute focused and affected-module validation.

## API and Compatibility

The Story adds a read-only Market Intelligence contract under the existing
versioned API namespace. Existing analysis, Opportunity, TradePlan, Risk and
execution contracts remain backward compatible.

The response must be provider-neutral. It may expose provider/source metadata as
provenance, but must not expose provider payloads, credentials or cTrader
protocol identifiers as domain fields.

Legacy Observations, Opportunities and manual TradePlans without thesis
references remain valid.

## Database

One new immutable thesis persistence boundary is expected.

The migration must provide:

- primary thesis identity;
- lineage/version uniqueness;
- state and market query fields;
- evaluation/cutoff/validity timestamps;
- composer/version/fingerprint provenance;
- analysis execution reference where available;
- serialized immutable structured payload.

No raw provider payload duplication, mutable current-thesis row, account-scoped
thesis table, Risk table, execution table, or monitoring table is authorized.

## Test Plan

### Domain and composer

Cover:

- `NO_TRADE`, `WATCH`, and `CANDIDATE` predicates;
- available/supporting evidence;
- missing and not-implemented evidence;
- stale required evidence;
- informational, weakening and blocking contradictions;
- multi-timeframe disagreement;
- structured confirmation and invalidation;
- structural level references;
- no confidence averaging;
- deterministic output from identical inputs;
- no Risk or execution authority.

### Persistence and pipeline

Cover:

- create and rehydrate exact thesis versions;
- lineage/version uniqueness;
- idempotent same-input composition;
- new version on changed inputs;
- historical immutability;
- valid-from/valid-until behavior;
- `NO_TRADE` persistence when no strategy matches;
- composition failure/degraded state;
- source fingerprint and provenance retention.

### Downstream compatibility

Cover:

- optional thesis reference in Opportunity provenance;
- optional thesis reference in TradePlan rationale/provenance;
- legacy and manual records without references;
- unchanged StrategyMatch and Opportunity state semantics;
- unchanged TradePlan lifecycle and Risk handoff.

### Read contract

Cover:

- exact thesis loading;
- latest valid market query;
- authentication and authorization;
- not found, stale, unavailable and degraded states;
- structured evidence and provenance serialization;
- no mutation through read operations.

### Commands

```text
mvn -f market-intelligence/pom.xml test
git diff --check
```

If downstream contract files are modified, run the corresponding affected
module tests. No frontend build, cTrader runtime validation, or broker sandbox
validation is required unless the implementation expands beyond this plan.

## Documentation Reconciliation

The implementation report must verify whether API contract documentation,
architecture references, migration notes, or user-facing intelligence
documentation require updates. ADR-076 and Story 0163 remain the canonical
architecture and scope sources.

No Obsidian write is authorized. Any transverse-memory outcome remains
proposal-only according to the Engineering Story workflow.

## Implementation Stop Conditions

Stop and request renewed human guidance if implementation requires:

- a new account or challenge authority inside Market Thesis;
- changes to analyzer semantics or provider-neutral Market Data contracts;
- replacing Observation, StrategyMatch, Opportunity, TradePlan, Risk or
  execution authority;
- AI-generated canonical thesis state;
- a universal evidence abstraction;
- a reference-market mapping decision;
- mutable historical thesis or TradePlan context;
- changing the approved ADR-076 scope.

## Approval Status

This Implementation Plan is complete and awaits explicit human approval before
implementation begins.
