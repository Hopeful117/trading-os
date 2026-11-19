# Implementation Plan - Story 0247-04

## Status

Prepared after human approval of the Repository Analysis. Implementation is not authorized until
this plan is explicitly approved.

## Implementation Objective

Add a deterministic, versioned, request-time ranking path for latest valid opportunities. The path
will combine exact `MarketThesis` evidence-quality facts with the existing business-priority score,
return a structured rationale, and leave opportunity, strategy, replay, risk and execution semantics
unchanged.

## Decisions

### Candidate lifecycle

The first ranking policy will consider only latest opportunities in these non-terminal states:

- `DETECTED`;
- `ANALYZED`;
- `ACTIVE`.

`CONSUMED`, `EXPIRED` and `INVALIDATED` opportunities will be excluded from the ranked candidate
set. The service will report the exclusion reason in its result diagnostics rather than silently
including them at the bottom.

### Evidence reference resolution

- An opportunity with a `MarketThesisReference` resolves the exact thesis ID and version through
  `MarketThesisRepository.findByIdAndVersion` or the existing exact-reference collection seam.
- The service must never substitute the latest thesis for an exact reference.
- A missing reference or unresolved exact thesis produces an excluded candidate with an explicit
  `MISSING_EXACT_EVIDENCE_REFERENCE` reason. It does not receive a fallback quality score.

### Ranking precedence

The policy will be lexicographic and versioned. It will not convert quality states into a weighted
numeric score.

For eligible candidates, the comparison order will be:

1. evidence eligibility tier;
2. contradiction state;
3. applicability state;
4. completeness state;
5. freshness state;
6. provenance state;
7. missing-policy state;
8. existing `OpportunityScore` descending;
9. opportunity UUID ascending;
10. opportunity version ascending.

`SUPPORTED` evidence will rank above `DEGRADED`, `UNAVAILABLE` and `BLOCKED`. A blocking
contradiction cannot be treated as a low business score. The exact enum-to-order mapping for each
dimension will be encoded in the policy and tested explicitly, including `NOT_APPLICABLE`.

The policy version will be a stable constant such as `opportunity-ranking/v1`. It will be included in
every ranking result and rationale.

### Missing quality assessment

An exact thesis without a persisted quality assessment is treated as unassessed evidence and is
excluded from the eligible ranked set with an explicit reason. This avoids silently ranking legacy
or partially migrated evidence as valid quality data.

### Result shape

The ranking application result will contain:

- policy version;
- ordered ranked entries;
- excluded candidate diagnostics;
- per-entry opportunity identity and version;
- deterministic ranking rationale;
- quality and thesis fingerprints when available.

The result will be request-time and non-persistent. It will not alter `TradingOpportunity`,
`MarketThesis`, `StrategyMatch` or `OpportunityScore`.

### Public exposure

This Story will implement the domain/application ranking contract and its internal orchestration.
It will not add Angular, Gateway or a new public API response. Existing controller sorting will not
be redefined as the evidence-based ranking contract. Public rationale exposure remains part of Story
`0247-05` or requires a separate approved API scope.

## Planned Changes

### New application/domain contracts

Add under the existing `market-intelligence` opportunity boundary:

- `OpportunityRankingCandidate` — immutable opportunity plus exact quality/thesis context;
- `OpportunityRankingPolicy` — versioned policy contract;
- `EvidenceBasedOpportunityRankingPolicy` — pure lexicographic comparator and rationale builder;
- `OpportunityRankingRationale` — policy version, dimension decisions, reason codes and fingerprints;
- `OpportunityRankingEntry` — ranked candidate and rationale;
- `OpportunityRankingResult` — ranked entries and excluded candidate diagnostics;
- `OpportunityRankingService` — repository orchestration and exact thesis resolution.

Names may be adjusted only if existing repository conventions provide an equivalent contract.

### Existing ranking engine

Extend `OpportunityRankingEngine` minimally with a candidate-ranking operation that consumes the new
candidate/policy boundary. Preserve the existing `TradingOpportunity` ranking method and
`highestScoreFirst()` behavior for existing callers and tests. The evidence-based policy must remain
composable and independently testable; it must not be hidden inside the web controller.

### Repository usage

- Reuse `TradingOpportunityRepository.findAllLatest()` for the bounded initial candidate set.
- Reuse `MarketThesisRepository` exact-reference lookup.
- Do not add a database join, migration or ranking persistence table.
- Do not change repository ownership or duplicate evidence-quality fields onto opportunities.

### Configuration

Register the policy and service in `OpportunityConfiguration` using existing repository beans. Keep
the policy stateless and deterministic.

## Test Plan

### Policy unit tests

Add deterministic fixtures covering:

- supported/fresh/complete evidence;
- stale evidence;
- partial or missing evidence;
- unavailable evidence;
- not-implemented evidence;
- weakening contradiction;
- blocking contradiction;
- not-applicable dimension;
- equal quality and equal business score;
- stable UUID/version tie-breaking;
- input collection order independence;
- policy version in rationale.

### Service tests

Use in-memory/fake repositories to verify:

- only latest non-terminal opportunities are candidates;
- terminal opportunities are excluded with explicit diagnostics;
- exact thesis versions are resolved;
- latest-thesis fallback never occurs;
- missing references and missing quality assessments are explicit exclusions;
- opportunities with different quality assessments are ordered by policy before business score;
- ranking does not mutate domain objects;
- replay and market-outcome repositories are never consulted.

### Regression tests

Preserve and run existing `OpportunityPoliciesTest` coverage for the original score-based ranking
engine. Add configuration/application tests only if the new service wiring requires them.

## Validation

Run from `market-intelligence`:

```bash
mvn -q -Dtest=OpportunityPoliciesTest,EvidenceQualityEvaluatorTest,<new-ranking-tests> test
mvn -q -DskipTests package
```

Also run:

```bash
git diff --check
```

The broader repository quality workflow remains applicable at PR validation time.

## Files Expected To Change

Likely files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityRankingEngine.java`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/` new ranking contracts and service;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/OpportunityConfiguration.java`;
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityPoliciesTest.java`;
- new ranking policy/service tests under the existing opportunity test package.

Expected unchanged areas:

- `StrategyEvaluator` and `StrategyMatch` contracts;
- `TradingOpportunity` and `OpportunityScore` semantics;
- `MarketThesis` and evidence-quality persistence schema;
- replay result persistence;
- risk, execution, broker, Gateway and frontend modules.

## Risks And Mitigations

- **False precision:** use lexicographic states, not weighted scores.
- **Look-up drift:** resolve exact thesis ID/version only.
- **Legacy ambiguity:** exclude missing quality/reference cases explicitly.
- **Presentation coupling:** keep policy in application/domain code, not controller sorting.
- **Scope expansion:** no public API, persistence or replay integration in this Story.

## Definition Of Done

- The approved policy and rationale are implemented and unit tested.
- Exact thesis provenance and quality fingerprints are preserved in ranking results.
- Terminal and unassessed candidates are explicit exclusions.
- Existing score ranking behavior remains unchanged.
- Targeted tests, package and `git diff --check` pass.
- No implementation begins before explicit human approval of this plan.
