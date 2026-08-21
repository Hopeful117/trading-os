# Story

## Metadata

**ID:** `0009`

**Title:** Strategy Domain Foundation

**Status:** In Progress

---

## Goal

Establish Strategy as a first-class bounded domain module inside Market
Intelligence, providing a stable, versioned, persistable
`StrategyDefinition` foundation for the future deterministic evaluator,
StrategyMatch provenance and strategy-targeted scanning described in ADR-034.

---

## Context

ADR-034 (Accepted) defines that Strategy owns the definition of what
constitutes a valid trading setup, that `StrategyMatch` will become a
persisted immutable fact, and that `TradingOpportunity` will eventually derive
from exactly one StrategyMatch.

Today, trading-setup semantics live implicitly inside
`OhlcTrendObservationRule` with no identity, version, lifecycle or validation
truth. Story 0009 creates the domain container those semantics will migrate
into.

Related: ADR-025 (Observation), ADR-026 (Trading Opportunity), ADR-033
(Scanner orchestration).

---

## Problem

There is no first-class Strategy concept in the codebase, so setup semantics
cannot be versioned, governed, attributed or validated, and future Stories
0010–0015 have nothing to build on.

---

## Scope

Introduce, inside market-intelligence, an isolated bounded module:

``` text
com.hope.trading.market_intelligence.strategy
├── domain        (StrategyDefinition, StrategyId, StrategyLifecycle,
│                 ValidationStatus, StrategyDirection,
│                 StrategyApplicability, RequiredSemanticInput,
│                 SemanticInputType, StrategyParameter(s))
├── application   (StrategyDefinitionRepository port)
└── adapter.persistence
                  (JPA entity, Spring Data repository, adapter)
```

Domain capabilities:

- explicit `StrategyId` + integer `version`; `(strategyId, version)` uniquely
  identifies exact deterministic semantics forever;
- semantic immutability per version; semantic evolution via `deriveVersion`
  creating a fresh DRAFT/UNVALIDATED definition;
- lifecycle `DRAFT → CANDIDATE → VALIDATED → ENABLED → RETIRED` with explicit
  legal transitions and terminal RETIRED;
- `ValidationStatus` (`UNVALIDATED | VALIDATED`) as metadata separate from
  lifecycle; VALIDATED lifecycle/ENABLED require recorded validation evidence;
- direction semantics `LONG | SHORT | DYNAMIC`;
- applicability value object (asset classes, timeframes, optional providers);
- required semantic inputs (`OBSERVATION|FEATURE:key`) decoupled from pipeline
  implementation types;
- typed deterministic parameters (DECIMAL, INTEGER, STRING, DURATION) with
  validated values;
- research/validation provenance reference placeholders;
- additive persistence (`V4__strategy_definition_foundation.sql`, table
  `strategy_definitions`, PK `(strategy_id, version)`);
- repository port `StrategyDefinitionRepository` with JPA adapter.

---

## Out of Scope

- StrategyEvaluator (Story 0010);
- bootstrap OHLC-trend strategy porting/seeding (Story 0010);
- StrategyMatch and its persistence (Story 0011);
- TradingOpportunity migration (Story 0012);
- Backtest (Story 0013) and validation workflow (Story 0014);
- Scanner strategy selection (Story 0015);
- REST API exposure;
- Quant Research, Trader Analytics, AI strategy generation;
- any change to Observation, OpportunityEngine, TradingOpportunity, ActiveScan,
  Risk or Broker behavior.

---

## Architecture / Invariants

Per ADR-034 non-negotiable invariants:

1. Strategy owns setup semantics; Scanner never does.
2. Strategy is an isolated bounded module; the intelligence pipeline must not
   reach into Strategy internals and Strategy must not depend on pipeline
   internals.
3. `(strategyId, version)` is immutable semantic identity.
4. Lifecycle governance and validation truth are separate dimensions.
5. The bootstrap legacy strategy (later story) carries
   `validationStatus = UNVALIDATED` and never counts as evidence.

---

## Acceptance Criteria

1. Strategy exists as a first-class domain concept isolated from pipeline
   implementation details (no dependency from `strategy.domain` on pipeline,
   application or persistence types).
2. Strategy identity and version are explicit; historical versions remain
   identifiable; exact-version retrieval works.
3. Deterministic semantics cannot silently change for an existing version;
   semantic evolution is expressed as a new version.
4. Lifecycle transitions follow ADR-034; illegal transitions are rejected;
   RETIRED is terminal.
5. Validation truthfulness is explicit and queryable
   (`validationStatus`, evidence reference).
6. Applicability, required semantic inputs and typed parameters can be
   represented and are validated.
7. StrategyDefinition persists and reloads without semantic loss; multiple
   versions coexist under one StrategyId.
8. Zero behavioral change to Observation production, OpportunityEngine,
   TradingOpportunity, AnalysisExecution, PipelineRun, ActiveScan, Passive
   Scanner, Market Data, Risk and Broker.
9. No REST endpoint, no seeded data, no evaluator, no StrategyMatch.
10. Full market-intelligence test suite passes.

---

## Validation Expectations

- Domain unit tests: identity/version invariants, immutability, lifecycle
  legality, validation semantics, direction/applicability/input/parameter
  rules.
- Persistence integration tests (H2 PostgreSQL mode, Flyway V1–V4): round-trip
  without semantic loss, multi-version history, governance evolution survival.
- Full module regression suite green.

---

## Story 0010 Handoff

At completion this story provides a stable, loadable, deterministic
`StrategyDefinition` (typed parameters, declared semantic inputs, applicability)
plus `StrategyDefinitionRepository` — everything the deterministic
StrategyEvaluator needs to consume definitions and produce evaluations,
without prescribing how evaluation works.
