# Repository Analysis - Story 0242

## 1. Repository Baseline

- **Story:** `0242 - Expose the Passive Awareness Lifecycle`
- **Analysis date:** 2026-11-14
- **Repository:** `trading-os`
- **Branch:** `story/0241-bounded-passive-dispatch`
- **HEAD:** `35cb6c6d74043e2f710a2b1be7bd0fad8b1de768`
- **Working tree:** clean before this documentation change.
- **Delivery evidence:** Stories 0239, 0240 and 0241 are merged through PRs
  #291, #292 and #293. CI passed for those PRs. No independent GitHub review
  was recorded for the merged PRs.

No application code, migration, runtime configuration, scheduler activation,
scan execution, broker operation or deployment is authorized by this analysis.

## 2. Scope and Authority

The authoritative inputs are:

- proposed Story 0242;
- accepted ADR-025, ADR-026, ADR-033, ADR-034, ADR-056, ADR-076, ADR-087,
  ADR-100 and ADR-102;
- approved Epic 0238;
- completed Stories 0239, 0240 and 0241;
- current Market Intelligence source, tests, migrations and configuration.

The repository is authoritative for current physical behavior. ADR-102
authorizes the Epic architecture and planning baseline, not implementation.
Story 0242 remains `Proposed - implementation not authorized`. Human approval
of this Repository Analysis is required before an Implementation Plan may be
created.

DevLog context was not available through the current tool set. No historical
decision is inferred from that absence. The Obsidian vault was not used because
the repository contains the governing Story and ADR artifacts.

## 3. Architectural Contract

ADR-102 defines passive monitoring as account-independent market awareness. It
must not be treated as account authorization, risk approval, broker execution or
autonomous trading.

The existing authority chain is:

```text
market facts
    -> deterministic analyses
    -> Observation
    -> Market Thesis
    -> StrategyMatch
    -> TradingOpportunity
    -> TradePlan
    -> Risk
    -> human authorization
    -> execution
```

Story 0242 must expose a read projection over the existing records. It must not
introduce `MarketCandidate`, replace `MarketThesis`, change Strategy semantics,
or make `OpportunityStatus.ACTIVE` mean account-actionable.

The qualifying passive candidate contract is the latest opportunity version
that has:

- `origin=PASSIVE_SCAN`;
- a required `strategyMatchId`;
- valid Observation and Market Thesis references;
- `MarketThesis.state=CANDIDATE`;
- a validity window containing projection time;
- canonical market/provider/product identity and passive provenance.

Missing, stale, unavailable or contradictory evidence must remain explicit and
must not be converted into a current candidate. Observation time, source
cutoff, completion/evaluation time, validity window and freshness diagnostics
must remain visible.

## 4. Existing Domain Model

### Observation

`Observation` is an immutable, versioned, evidence-bearing deterministic fact.
Its lifecycle is `ACTIVE`, `SUPERSEDED` or `EXPIRED`. The existing observation
repositories and references are the authoritative fact layer and must be
reused.

Relevant source:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/observation/Observation.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ObservationRepository.java`

### Market Thesis

`MarketThesis` is market-scoped, strategy-neutral, immutable and versioned. Its
states include `NO_TRADE`, `WATCH` and `CANDIDATE`. Required stale evidence or a
blocking contradiction prevents a fresh `CANDIDATE` result. Thesis freshness
cannot exceed the freshness of required evidence.

Relevant source:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesis.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisState.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/MarketThesisRepository.java`

### StrategyMatch and TradingOpportunity

ADR-034 defines `StrategyMatch` as the immutable fact that a specific strategy
version matched. `TradingOpportunity` is derived from that match and remains a
trader-facing candidate, not a TradePlan or authorization.

The current `TradingOpportunity` already carries the required projection
inputs: immutable `origin`, `strategyMatchId`, Observation references,
`marketThesis`, `marketId`, instrument, direction, evaluated/validity timestamps,
account provenance, source scan provenance and analysis execution provenance.

Relevant source:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/TradingOpportunity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/OpportunityOrigin.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/TradingOpportunityRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityEngine.java`

### Passive provenance

Stories 0239-0241 persist the passive universe identity, passive run and
per-market execution linkage. The passive path uses `AnalysisExecution` in
`PASSIVE` mode and existing pipeline outputs. Story 0242 should project these
references rather than reconstructing provenance from instrument names or
timestamps.

Relevant source:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveScanRun.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveScanMarket.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/ProductionIntelligencePipeline.java`

## 5. Current Read and Projection Boundaries

The current opportunity web projection exposes active filtering through
`OpportunityController`. Existing active scan projection types also preserve
strategy provenance. These patterns can be reused, but passive awareness must
not be presented as an active-only or account-actionable view.

The implementation should prefer a bounded read contract with:

- explicit passive origin;
- deterministic filters for market, provider/product identity, direction,
  thesis state, freshness and validity;
- stable ordering using validity/evaluation timestamps and canonical identity;
- bounded page size;
- no global cross-market ranking;
- no account-specific fields as authorization inputs.

The projection must select the latest valid version without mutating historical
opportunities, theses or observations. Superseded and expired records remain
queryable for history but must not appear as current candidates.

## 6. TradePlan Boundary Finding

The specialized internal opportunity path is guarded:

- `OpportunityTradePlanGenerationService` requires an active opportunity;
- it validates Market Thesis lineage, freshness, validity and `CANDIDATE` state;
- it rejects an opportunity whose `accountId` is absent or differs from the
  requested account with `OPPORTUNITY_ACCOUNT_MISMATCH`;
- `OpportunityTradePlanGenerationServiceTest.rejectsAccountlessOpportunity`
  covers this specialized path.

However, the exclusive planning engine currently checks only opportunity status,
instrument and direction in `TradePlanningEngine.compatible(...)`. It does not
reject `OpportunityOrigin.PASSIVE_SCAN` with no account identity. The generic
path is reachable through:

```text
TradePlanController
    -> TradePlanApplicationService.create
    -> TradePlanningEngine.plan
```

The generic `TradePlanningRequest` carries a planning context and actor but no
explicit account identifier; the engine can therefore accept an accountless
opportunity while a valid account-bound planning context exists. Existing
`TradePlanningEngineTest` coverage validates status and planning behavior but
does not cover this negative boundary.

This is a confirmed implementation gap against Story 0242 acceptance criterion
11 and ADR-102. It is a safety blocker for implementation approval, not a
request to expand this documentation-only change into a code fix. The future
implementation must enforce the invariant at the authoritative planning
boundary and add a regression test there, rather than relying only on the
specialized internal service.

## 7. Freshness, Contradiction and Deduplication

The projection must reuse existing semantics rather than introduce a universal
TTL:

- opportunity `validFrom`/`validUntil` controls opportunity validity;
- thesis `validFrom`/`validUntil`, `evaluatedAt` and `sourceCutoff` control thesis
  eligibility;
- required evidence freshness controls whether a thesis can remain a current
  `CANDIDATE`;
- `WATCH`, `NO_TRADE`, missingness and contradiction diagnostics remain visible;
- opportunity versioning and the existing fusion/deduplication policy prevent
  repeated equivalent detections from flooding the feed.

ADR-056 and ADR-076 require non-positive evidence to remain explicit. The
projection must not infer support from missing data, use a stale thesis as
current, hide a blocking contradiction or replace structured diagnostics with a
single quality score.

## 8. Authorization and Data Safety

Passive monitoring and awareness are account-independent. Read authorization
must prevent cross-user leakage where user-specific projections are introduced,
but the market-level passive candidate itself must not acquire account
authorization semantics.

The projection must not expose broker credentials, provider payloads or
account/challenge data as part of the passive candidate contract. Canonical
market identity must preserve the market ID, provider and product type; provider
symbol details must not be guessed or collapsed into the canonical identity.

Existing service security and Gateway boundaries remain authoritative. No new
internal endpoint may be made public by this Story, and no account
synchronization or broker operation is required for passive awareness.

## 9. Implementation Feasibility and Boundaries

The Story is implementable as an additive read projection using existing
records, repositories and web projection conventions, subject to resolving the
TradePlan blocker above.

Expected implementation concerns are:

- a read-only passive awareness application contract;
- repository queries or bounded in-memory composition that select latest valid
  versions without N+1 unbounded loading;
- explicit evidence-state and freshness diagnostics;
- deterministic ordering and bounded filters;
- response DTOs that preserve canonical identity and provenance;
- authorization tests for user/account boundaries;
- negative tests for missing, stale, contradictory, expired and superseded data;
- a planning-boundary regression test for accountless passive opportunities.

No new aggregate, scheduler, AI integration, ranking engine, active escalation,
notification channel, migration or broker integration is justified by the
current Story.

## 10. Required Decisions Before Implementation

The following are implementation-level decisions that can remain within the
approved architecture after this analysis is accepted:

- exact read endpoint and response projection shape;
- query composition and pagination contract;
- deterministic ordering tie-breakers;
- diagnostic representation for missing/stale/contradictory evidence;
- whether passive awareness is exposed through a new controller or an existing
  read controller extension.

The following are not implementation choices and must not be silently changed:

- no `MarketCandidate` aggregate;
- passive is account-independent;
- `ACTIVE` is a lifecycle state, not trade readiness;
- a passive opportunity requires valid thesis and strategy invariants;
- accountless passive opportunities cannot enter TradePlan generation;
- no projection-layer authorization of Risk or execution.

## 11. Validation Expectations

Validation should cover:

- observation-only records are not candidates;
- qualifying passive opportunity plus valid thesis produces one projection item;
- missing strategy, observation or thesis references are non-positive;
- stale, unavailable or contradictory required evidence is visible and not
  current/actionable;
- expired and superseded versions are excluded from current results;
- repeated equivalent detections remain deduplicated;
- canonical market/provider/product identity and provenance are preserved;
- deterministic ordering and bounded filtering hold;
- cross-user read authorization is enforced;
- the generic TradePlanningEngine rejects accountless passive opportunities;
- no passive projection creates a TradePlan, Risk decision or execution command.

## 12. Conclusion and Approval Gate

Story 0242 fits the accepted ADR-102 architecture and can reuse the existing
Observation, Market Thesis, StrategyMatch, TradingOpportunity, passive run and
active projection boundaries. The current repository does not yet satisfy the
full safety invariant because the generic TradePlan planning boundary lacks the
accountless passive-opportunity rejection enforced by the specialized path.

This Repository Analysis is complete for human review. No Implementation Plan
should be created until the analysis is approved and the planning-boundary
blocker is explicitly accepted as implementation scope for Story 0242.
