# Story 0112B - Repository Analysis

## Status

Completed for human approval before Implementation Planning.

## Scope Reviewed

This analysis covers the approved Story 0112B, ADR-056, ADR-060, ADR-063, the
completed Story 0112A Order Book replay implementation, the current Market
Intelligence context and capability boundaries, and the existing FeatureSnapshot
projection.

DevLog was queried for Story 0112B context but was unavailable because the
configured provider reported exhausted API quota. No Obsidian vault consultation
was required. The current repository and accepted ADRs remain authoritative.

## Current Repository State

- Current branch: `main`.
- Story 0112A is merged into the current repository history.
- Unrelated pre-existing worktree changes are preserved and are not part of this
  analysis:
  - `docker-compose.yml`;
  - `market-data/src/main/java/com/hope/trading/market_data/service/MarketSynchronizationImpl.java`;
  - `research/ml/src/research_dataset/__pycache__/`.
- Story 0112B now exists at
  `docs/architecture/stories/0112B-deterministic-liquidity-intelligence/story.md`
  with status `Approved` after explicit human approval.

## Architectural Context

The approved responsibility chain is:

```text
Market Data durable evidence
    -> strict Order Book replay at T0
    -> Market Intelligence OrderBookContext
    -> deterministic liquidity analyzers
    -> typed findings/artifacts and, if approved, a new feature projection
```

Market Data remains authoritative for normalized levels, sequence validity,
known-at eligibility, and replay truth. Market Intelligence may calculate
measurements but must not make Risk, authorization, Opportunity, TradePlan, or
execution decisions.

ADR-056 requires pure deterministic calculations and explicit degraded states.
ADR-060 requires immutable versioning and provenance when a FeatureSnapshot is
involved. ADR-063 requires Order Book replay to remain the only historical input;
current provider state must not be used as a fallback.

## Existing Order Book Replay Boundary

Story 0112A currently provides the following Market Data types:

- `OrderBookEvidence` for immutable normalized snapshots and deltas;
- `OrderBookReplayService` for cutoff replay;
- `OrderBookReplayResult` with status, sequence ID, cutoff, latest `knownAt`,
  levels, and an unavailable reason.

`OrderBookReplayService.replay(marketId, depth, cutoff)` validates its arguments,
queries trusted evidence with the cutoff, selects a trusted snapshot, applies
same-sequence contiguous deltas, limits the result to the requested depth, and
returns `UNAVAILABLE` when no trusted snapshot or contiguous reconstruction exists.

The current replay result does not expose all provenance required by Story 0112B:

- source evidence fingerprints or an evidence range;
- normalization version;
- occurred-at boundary;
- explicit freshness/staleness metadata;
- a distinct `INVALID` replay status.

The current Market Data replay service is also not exposed through a Market Data
HTTP endpoint or a Market Intelligence client. `market-intelligence` cannot
directly depend on Market Data implementation classes. A replay transport
contract and adapter boundary are therefore required before a production
Market Intelligence analyzer can consume historical Order Book data.

This is a confirmed implementation gap, not permission to use current provider
state. The implementation plan must decide the smallest compatible replay
contract extension and its ownership before implementation starts.

## Existing Market Intelligence Boundaries

Market Intelligence already provides:

- `IntelligenceAnalysisRequest` with an optional evaluation cutoff and evidence
  acquisition policy;
- `IntelligenceContext` and typed context sections;
- deterministic capabilities such as `SpreadAnalysisCapability`;
- artifact types and immutable `StoredArtifact` provenance;
- `FeatureProjectionV1` and immutable `FeatureSnapshot` persistence.

The existing `SpreadAnalysisCapability` consumes `MarketSnapshotContext`, which
contains only the current top bid/ask snapshot. Its live analysis path uses the
current market context and generation time, so it cannot be reused as the
historical Order Book analyzer without a distinct replay-backed input contract.

No `OrderBookContext`, Order Book context contributor, replay client, liquidity
capability, liquidity artifact type, or Order Book feature group currently exists.

`ContextSectionType` has no Order Book section. `MarketDataClient` currently
supports market catalogue, current price snapshots, OHLC, and market facts, but
no historical Order Book replay endpoint.

## Existing Feature Projection

`FeatureProjectionV1` is a stable ordered schema containing OHLC, Market
Structure, and Trend features. `FeatureGroup` currently contains only:

```text
OHLC, STRUCTURE, TREND
```

`FeatureSnapshotProjectionService` validates strict observed execution, context
fingerprints, producing execution identity, market scope, and artifact cutoff
before projecting and persisting a snapshot.

Adding Order Book liquidity values to `FeatureProjectionV1` would silently change
its schema and violate ADR-060. A future integration must use a separately named
projection version and preserve the existing V1 projection unchanged. The
implementation plan must also decide whether 0112B initially produces a
standalone Market Intelligence artifact or includes a new immutable projection.

Existing `FeatureUnit` supports price, ratio, decimal fraction, basis points, and
count, but it has no dedicated quantity/volume unit. The selected liquidity
feature definitions must either use an existing semantically correct unit or
introduce a narrowly scoped unit with explicit versioning.

## Candidate Measurements and Decisions Required

ADR-063 identifies spread, depth, imbalance, microprice, and liquidity
concentration as candidate deterministic features. The repository currently
contains a quote-quality calculator for top bid/ask spread, but no Order Book
feature formulas for:

- depth aggregation and level bounds;
- bid/ask imbalance denominator and zero-volume behavior;
- microprice validity and weighting;
- liquidity concentration definition and level/window policy.

The Implementation Plan must explicitly select the initial feature set and define
each formula, scale, rounding policy, input validity rule, missingness status,
depth/window parameter, algorithm version, and fingerprint material. No formula
should be inferred from an existing feature name or provider behavior.

Absorption and trade-flow analysis remain outside the Story.

## Required Implementation Boundaries

The implementation will need distinct boundaries for:

1. A transport-neutral replay response owned by Market Data and a client adapter
   owned by Market Intelligence.
2. A provider-neutral `OrderBookContext` carrying levels and complete replay
   provenance at the evaluation cutoff.
3. Pure deterministic liquidity calculations independent of Spring, network calls,
   clocks, and mutable global state.
4. Explicit result statuses for unavailable, insufficient, invalid, and stale
   inputs.
5. A versioned liquidity feature/artifact contract with deterministic ordering and
   fingerprinting.
6. Optional immutable feature projection integration that cannot mutate V1.

The current `MarketSnapshotContext` and `SpreadAnalysisCapability` should not be
expanded into a historical Order Book contract because they represent a current
quote context and have different provenance semantics.

## Risks and Gaps

### Confirmed Gaps

- No cross-service replay API exists.
- `OrderBookReplayResult` lacks the full provenance required for downstream
  feature lineage.
- No Market Intelligence Order Book context section or contributor exists.
- No deterministic Order Book liquidity analyzer exists.
- No liquidity feature group or versioned projection exists.
- Feature unit and projection models may need narrowly scoped additions for
  quantity-based values.

### Architectural Risks

- Calling Market Data's current quote endpoint would violate strict historical
  replay semantics.
- Adding Order Book values to `feature-projection-v1` would mutate an immutable
  projection contract.
- Making a liquidity analyzer depend directly on Kraken DTOs would leak provider
  infrastructure into Market Intelligence.
- Unspecified formulas could create non-reproducible results or hidden product
  decisions.
- An incomplete replay provenance contract would make downstream fingerprints
  unable to identify the exact source evidence.

## Proposed Validation Surface

The implementation should add focused tests for:

- replay transport mapping and unavailable/invalid responses;
- `OrderBookContext` validation and provenance preservation;
- best bid/ask ordering, crossed books, empty books, and invalid levels;
- each selected liquidity formula, including zero quantities and boundary depth;
- explicit missing, stale, invalid, and unavailable outcomes;
- deterministic output ordering and fingerprints;
- feature/artifact provenance to sequence, cutoff, and source fingerprints;
- protection of `feature-projection-v1` if a new projection is introduced;
- Market Intelligence architecture constraints and service-level verification.

Validation should include:

- `cd market-intelligence && ./mvnw verify`;
- Market Data targeted tests if the replay contract changes;
- SonarQube for affected projects when configured;
- `git diff --check`;
- representative replay-to-feature integration validation without current-state
  fallback.

## Recommended Planning Sequence

The following is an input to Implementation Planning, not an approved plan:

1. Confirm the minimum replay response provenance required by the Story and
   determine whether the narrow extension belongs in Market Data.
2. Define the cross-service replay contract and Market Intelligence adapter
   without exposing provider-specific DTOs.
3. Select the initial liquidity measurements and approve exact formulas,
   parameters, missingness, units, and algorithm version.
4. Define `OrderBookContext` and the deterministic analyzer/artifact boundary.
5. Decide whether to deliver standalone artifacts first or integrate through a
   new versioned FeatureSnapshot projection.
6. Implement pure calculations and focused tests.
7. Validate temporal provenance, determinism, service boundaries, and quality
   gates.

## Decision and Recommendation

The repository has a suitable deterministic Market Intelligence and immutable
FeatureSnapshot foundation, but it does not yet have the replay transport or
provenance contract required by Story 0112B. The Story is implementable without a
new service or infrastructure platform, provided the plan first resolves this
narrow contract gap and explicitly defines the initial liquidity formulas.

Repository Analysis recommendation: **READY FOR IMPLEMENTATION PLANNING**, subject
to human approval of this analysis and explicit planning decisions for replay
provenance, feature formulas, and projection integration.
