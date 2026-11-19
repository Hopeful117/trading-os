# Repository Analysis - Story 0247-04

## Scope

This analysis covers Story `0247-04 - Evidence-Based Opportunity Ranking`.
The Story was approved by the human owner for Repository Analysis. Implementation is not
authorized by this document.

The repository is currently at `main` revision `77e8ef9156fe8859cb60b2935cb9383725766281`, with
the Story artifact present as an uncommitted file. The previous Story `0247-03` is merged. DevLog
was queried for applicability, but its returned context was truncated, low confidence and did not
retain the complete Story or source files. The current repository, canonical Story and accepted ADRs
are therefore authoritative for this analysis.

## Existing Contracts

### Opportunity identity and business priority

`TradingOpportunity` is an immutable versioned domain object. It contains:

- opportunity identity and version;
- lifecycle status;
- instrument, direction, scenario, timeframe and type;
- `OpportunityScore`;
- explanations and observation references;
- strategy-match, market, scan and analysis-execution lineage;
- an optional `MarketThesisReference`;
- optional setup snapshot and account/source-scan references.

`OpportunityScore` is explicitly constrained to the range `0..100` and documents the semantic
boundary `BUSINESS_PRIORITY_NOT_PROBABILITY`. It is therefore a valid ranking dimension, but it
must not be relabeled as quality, confidence, probability or profitability.

`DeterministicOpportunityFusionPolicy` currently derives the business score from the average
observation confidence and maps the timeframe to an opportunity type. This is opportunity creation
behavior, not a complete evidence-based ranking policy. Story 0247-04 must not silently change that
fusion behavior.

### Existing ranking primitive

`OpportunityRankingEngine` currently accepts:

- a collection of opportunities;
- an `OpportunityRankingStrategy` exposing a comparator;
- a filter predicate.

It applies the filter, then the supplied comparator, then stable tie-breakers based on opportunity
ID and version. `highestScoreFirst()` is the only built-in strategy and orders by descending
`OpportunityScore` through the comparator contract.

The engine is registered as a Spring bean in `OpportunityConfiguration`, but repository search found
no production caller. Its only current usage is the ranking unit test. The existing public
`OpportunityController` independently loads all latest opportunities, filters them, sorts them by a
requested presentation field and applies in-memory pagination. Its `score` sort is presentation
sorting, not the application ranking policy described by this Story.

### Evidence quality

`EvidenceQualityAssessment` is an immutable, non-probabilistic contract with:

- policy version;
- evaluation and source-cutoff timestamps;
- quality status;
- dimension assessments;
- reason codes;
- evidence provenance;
- input and result fingerprints.

The dimensions are:

- `COMPLETENESS`;
- `FRESHNESS`;
- `PROVENANCE`;
- `CONTRADICTION`;
- `APPLICABILITY`;
- `MISSING_POLICY`.

Dimension states include satisfied, partial, missing, stale, contradicted, unavailable,
not-implemented and not-applicable. Overall statuses are `SUPPORTED`, `DEGRADED`, `BLOCKED` and
`UNAVAILABLE`.

`EvidenceQualityEvaluator` is a pure evaluator over an assembled `MarketThesis`. It canonicalizes
evidence ordering, evaluates the six dimensions, produces deterministic reasons and fingerprints,
and does not produce a numeric quality score. This is important: Story 0247-04 must define an
explicit ordering policy over these states rather than inventing a hidden numeric conversion.

The evaluator treats blocking contradiction as `BLOCKED`, fully satisfied or not-applicable
dimensions as `SUPPORTED`, unavailable evidence as `UNAVAILABLE`, and other incomplete states as
`DEGRADED`. It also checks source-cutoff and evidence freshness semantics.

### Market thesis linkage

`MarketThesis` is immutable and versioned. It stores the complete `EvidenceQualityAssessment` and
its source evidence. `MarketThesisRepository` can retrieve an exact thesis by ID and version and
can resolve a collection of `MarketThesisReference` values.

`TradingOpportunity` stores only an optional `MarketThesisReference`; it does not embed the thesis or
the evidence-quality assessment. `OpportunityController` resolves the referenced thesis only for
detail responses when requested. The list response does not currently expose evidence-quality
dimensions or ranking rationale.

`JpaTradingOpportunityRepository.findAllLatest()` returns latest versions, but it orders them by
creation time and ID. It does not join or load referenced theses. A ranking application service must
therefore resolve exact thesis references explicitly if evidence quality is part of ranking.

### Persistence and immutability

Opportunities are append-only versioned facts. `JpaTradingOpportunityRepository.append` enforces
sequential versions per opportunity. Market theses use exact version references and immutable
fingerprinted persistence.

The Story draft recommends request-time ranking and explicitly excludes a persisted global ranking
snapshot. No new ranking persistence boundary is justified by the current repository evidence.

## Architectural Constraints

The following constraints are authoritative:

- Market Data remains authoritative for provider-neutral market identity and observed facts.
- Market Intelligence owns evidence composition, opportunity interpretation and ranking policy.
- `StrategyEvaluator` and `StrategyMatch` remain authoritative for setup truth.
- Ranking is downstream prioritization among valid matches and must not change `MATCH`, `NO_MATCH`,
  `NOT_EVALUABLE` or `FAILED` semantics.
- `OpportunityScore` remains business priority and must not become probability or profitability.
- Trading Core and Risk Domain remain authoritative for account context, risk, authorization and
  execution.
- Passive monitoring remains account-independent and cannot create trading authority.
- AI remains optional and advisory.
- No new microservice, provider, workflow engine, ActiveScan creation path, broker path or execution
  path is justified.
- Replay and market-outcome artifacts may validate reproducibility but must not become implicit
  predictive ranking inputs.

These constraints are consistent with ADR-033, ADR-034, ADR-035 and the Epic 0247 boundary.

## Proposed Implementation Boundary

The smallest safe implementation should preserve the existing ranking primitive while adding an
explicit policy and application seam:

1. Define a ranking input/value boundary that combines a latest `TradingOpportunity` with the exact
   referenced `MarketThesis` quality assessment when one exists.
2. Define a versioned ranking policy that compares evidence states lexicographically and then applies
   the existing business-priority dimension.
3. Keep policy evaluation pure and deterministic. It must not call providers, read the current
   market, mutate opportunities or consult risk/execution state.
4. Produce a structured ranking rationale containing the policy version, ordered dimensions,
   evidence references or fingerprints, reasons and the final tie-break result.
5. Reuse the existing stable opportunity ID/version tie-breaker. Operational timestamps must not be
   the final deterministic tie-breaker unless a concrete semantic requirement is demonstrated.
6. Add an application service or adapter-level orchestration only where required to resolve exact
   thesis references and call the policy. Do not make the controller independently reconstruct
   ranking semantics.
7. Preserve the existing `OpportunityRankingEngine` as the generic ordering primitive or refactor
   it minimally so the policy remains composable and testable.
8. Keep the first version request-time and non-persistent. A ranking rationale can be returned as an
   application result without adding a ranking table or migration.

The exact precedence of `SUPPORTED`, `DEGRADED`, `UNAVAILABLE` and `BLOCKED`, and whether an
ineligible opportunity is excluded or retained with a non-prioritized outcome, remain decisions for
the Implementation Plan. They must not be inferred solely from enum declaration order.

## Data and Contract Gap

The main integration gap is that evidence quality is attached to `MarketThesis`, while ranking
operates on `TradingOpportunity`. The repository currently supports exact reference resolution, but
the existing list endpoint and ranking engine do not provide a combined ranked projection.

The analysis does not justify adding evidence-quality fields directly to `TradingOpportunity`. That
would duplicate thesis-owned evidence and create a new synchronization problem. The preferred
boundary is an ephemeral ranking input/result that retains the thesis reference and quality
fingerprint/rationale for traceability.

If a consumer requires a public ranking rationale, that should be handled by Story 0247-05 or by a
separate explicitly approved API extension. Story 0247-04 should establish the deterministic domain
and application contract without broadening the frontend or public API scope.

## Main Risks

- Assigning numeric weights to qualitative evidence states could create false precision and violate
  the non-probabilistic boundary.
- Treating `BLOCKED` or `UNAVAILABLE` as an ordinary low score could allow inadmissible evidence to
  remain indistinguishable from valid low-priority evidence.
- Treating evidence quality as part of `TradingOpportunity` identity would duplicate immutable
  thesis-owned data and complicate versioning.
- Resolving only the latest thesis instead of the exact `MarketThesisReference` would break replay
  and auditability.
- Using `createdAt`, current provider data or collection order as a ranking dimension would reduce
  reproducibility.
- Reusing replay or market-outcome results as ranking inputs could introduce unapproved predictive
  or profitability semantics.
- Updating only `OpportunityController` would create a presentation-specific ranking policy instead
  of a reusable Market Intelligence contract.
- Returning only a reordered list without rationale would satisfy sorting but not the Story's
  explainability requirement.
- A policy that ranks all latest opportunities without explicit lifecycle/eligibility treatment
  could cause expired, superseded or otherwise invalid candidates to outrank valid opportunities.

## Unknowns Requiring Resolution During Planning

- Which opportunity statuses and lifecycle conditions define a valid ranking candidate?
- Should `BLOCKED`, `UNAVAILABLE`, `DEGRADED` and missing thesis references be excluded, retained at
  the bottom, or represented through separate ranking outcome states?
- What is the approved lexicographic precedence among contradiction, applicability, completeness,
  provenance, freshness and missing-policy dimensions?
- Should a missing `MarketThesisReference` be a hard ranking failure, a non-prioritized candidate,
  or a supported legacy compatibility case?
- Is the ranking contract internal-only for this Story, or must an existing internal/public response
  expose the rationale immediately?
- Does the existing `OpportunityScore` remain the final dimension after evidence eligibility, or does
  the policy need a separate explicit business-priority accessor?
- Should replay comparison be represented only in tests, or also in a non-persistent validation
  report?

## Validation Plan

### Domain/application tests

- Same semantic inputs produce identical ranking order and rationale.
- Input collection order does not affect the result.
- Stable opportunity ID/version tie-breaking is deterministic.
- Business priority remains distinct from evidence quality.
- Policy version changes are visible without mutating opportunities.

### Evidence-quality fixtures

- Supported/fresh/complete evidence.
- Stale evidence.
- Partial or missing evidence.
- Unavailable evidence.
- Not-implemented policy evidence.
- Weakening and blocking contradictions.
- Not-applicable thesis quality.
- Missing or mismatched exact thesis reference.

### Boundary tests

- Expired and superseded opportunities cannot silently rank as valid candidates.
- Ranking does not mutate `TradingOpportunity`, `MarketThesis` or `StrategyMatch`.
- Ranking does not call provider or execution services.
- Replay/market-outcome data is not consumed as a ranking dimension.
- Existing `OpportunityRankingEngine` stable behavior remains covered.

### Repository validation

- Targeted `market-intelligence` Maven tests.
- Relevant persistence/application tests if the orchestration changes.
- Applicable module quality verification.
- `git diff --check`.

## Affected Areas

Likely affected areas, subject to the approved Implementation Plan:

- `market-intelligence/.../application/opportunity/OpportunityRankingEngine.java`;
- `market-intelligence/.../application/opportunity/OpportunityRankingStrategy.java`;
- new ranking policy/value objects under the existing opportunity application/domain boundary;
- `market-intelligence/.../application/port/TradingOpportunityRepository.java` only if an exact
  retrieval seam is needed;
- `market-intelligence/.../application/port/MarketThesisRepository.java` for exact thesis resolution
  reuse, not contract duplication;
- ranking and evidence-quality tests.

The following areas should remain unchanged unless analysis during planning demonstrates a concrete
contract gap:

- `StrategyEvaluator` and strategy-match semantics;
- `OpportunityScore` meaning and fusion calculation;
- risk, execution, broker and Trading Core modules;
- frontend and Gateway contracts;
- replay-result persistence and market-outcome semantics.

## Conclusion

The repository already contains the required deterministic primitives: immutable opportunities,
stable business scores, exact thesis references, versioned evidence-quality assessments and a generic
stable ranking engine. The missing capability is not another score calculation; it is a versioned
policy and traceable ranking result that composes those existing facts without changing their
semantics.

The safest implementation is a pure, lexicographic, request-time ranking policy plus a narrow
application seam that resolves exact thesis versions. It should retain evidence quality and business
priority as separate dimensions, expose rationale, and leave public/frontend exposure and replay
integration outside this Story.
