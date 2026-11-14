# Implementation Plan - Story 0242

## Overview

Implement a bounded, read-only passive awareness projection in
`market-intelligence` using the existing `Observation`, `MarketThesis`,
`StrategyMatch` and `TradingOpportunity(PASSIVE_SCAN)` records. The projection
will expose deterministic candidate eligibility, freshness, evidence
diagnostics, canonical identity and passive provenance without introducing a
`MarketCandidate` aggregate or changing existing analytical semantics.

The plan also closes the safety gap identified during Repository Analysis: the
authoritative `TradePlanningEngine` boundary must reject accountless passive
opportunities, not only the specialized internal opportunity-to-plan service.

This plan is based on the human-approved Repository Analysis for Story 0242. It
is a technical recommendation only. It does not authorize implementation,
migrations, runtime activation or deployment.

## Planned Changes

### 1. Define the passive awareness read contract

Add a focused application read contract and response model for passive
awareness. The contract should represent:

- canonical market ID, instrument, provider and product type;
- opportunity ID and exact version;
- `PASSIVE_SCAN` origin and passive run/execution provenance;
- strategy match, Observation and Market Thesis references;
- direction, scenario and opportunity lifecycle state;
- observation/evaluation time, evidence cutoff, completion time and validity;
- freshness, quality, missing-evidence and contradiction diagnostics.

The contract must not contain account authorization, risk approval, execution
parameters, broker credentials or provider payloads.

### 2. Compose only qualifying current candidates

Implement a deterministic projection service over existing repositories.

The current candidate selection must require:

- latest opportunity version;
- `origin=PASSIVE_SCAN`;
- `OpportunityStatus.ACTIVE` as lifecycle state only;
- non-null strategy-match provenance;
- resolvable Observation references;
- resolvable Market Thesis reference;
- `MarketThesisState.CANDIDATE`;
- thesis and opportunity validity at the projection clock;
- compatible market/provider/product identity;
- no stale or blocking required evidence.

Non-qualifying records must remain observable through explicit diagnostics where
the read contract supports them, but must not be emitted as current/actionable
passive candidates.

Use the existing opportunity fusion/versioning semantics. Do not create a new
aggregate or mutate any source record.

### 3. Add bounded deterministic filtering and ordering

Expose bounded filters for the supported awareness dimensions, such as market,
provider/product identity, direction, thesis state and freshness eligibility.
Apply a fixed maximum page size and stable tie-breakers after the semantic
ordering fields. Do not add global cross-market ranking or a new score.

Repository access must avoid unbounded N+1 loading. If existing repository
ports cannot provide the required bounded read efficiently, add narrow query
methods or an adapter projection without changing the domain authority.

### 4. Expose the read-only endpoint through existing security boundaries

Extend the existing opportunity read/controller conventions or add a focused
passive-awareness controller, depending on the established mapping pattern.
Use the existing versioned Gateway/service boundary and `ResponseEntity`
conventions.

The endpoint must be read-only, must preserve passive origin explicitly and
must not be routed through active-scan or account-actionable views. Existing
JWT/service authorization must remain intact; no endpoint is made public or
internal-only by assumption.

### 5. Enforce the TradePlan safety boundary

Update the authoritative `TradePlanningEngine` validation path so an
accountless passive opportunity cannot produce a TradePlan through the generic
TradePlan API. Preserve the existing specialized rejection behavior and use a
single domain-level invariant rather than duplicating checks in controllers.

The validation must continue to allow account-bound opportunities only when the
planning context is authorized and compatible. Manual TradePlan creation is
unchanged because it does not originate from a TradingOpportunity.

### 6. Add regression and projection tests

Add focused tests for positive and non-positive projection outcomes, bounded
ordering/filtering, provenance and authorization. Add a direct
`TradePlanningEngine` regression test proving that an accountless passive
opportunity is rejected even when a valid account-bound planning context exists.

### 7. Reconcile documentation

After implementation, inspect only the canonical API/architecture documentation
affected by the new read contract. Update it if the repository documents the
new endpoint or passive awareness behavior. Record `Documentation update:
Required` or `Not required` in the Implementation Report. Do not create a
changelog or broad documentation rewrite.

## Files to Modify

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanningEngine.java`
  - Enforce the accountless passive-opportunity rejection at the authoritative
    planning boundary.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityController.java`
  - Extend only if the existing controller is the correct bounded read entry
    point; otherwise keep unchanged and use a focused controller.
- Existing repository ports/adapters under
  `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port`
  and `adapter/persistence`
  - Add only narrow bounded query support if required to prevent N+1 reads.
- Existing security/Gateway route configuration only if the chosen read endpoint
  requires an explicitly versioned route.

## Files to Create

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/passive/PassiveAwarenessProjectionService.java`
  - Compose the deterministic read projection from existing authorities.
- A small set of passive-awareness query/response records under the existing
  application/adapter web package conventions.
  - Carry bounded filters, pagination, identity, provenance and diagnostics.
- Focused projection/controller tests under
  `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/passive`
  and/or `adapter/web`.
  - Validate all Story acceptance boundaries.

No new aggregate, Flyway migration or external dependency is planned.

## Dependencies

- Story 0239 passive universe identity and Story 0240 passive lifecycle are
  prerequisites and remain unchanged.
- Story 0241 bounded passive dispatch is the producer of passive execution
  provenance and remains unchanged.
- Existing Observation, Market Thesis, StrategyMatch and TradingOpportunity
  repositories are the source authorities.
- Existing Market Data catalogue contracts provide canonical provider/product
  identity; identity must not be inferred from instrument text.
- Existing Spring Security, Gateway and REST conventions remain in force.
- No new external library or service dependency is required.

Implementation order is strict: domain/read contract, source composition,
bounded query and ordering, endpoint/security mapping, TradePlan invariant,
tests, then documentation reconciliation.

## Test Plan

### Projection tests

- Observation-only input is not projected as a candidate.
- A qualifying passive opportunity with valid StrategyMatch, Observation and
  `MarketThesisState.CANDIDATE` produces exactly one item.
- Missing StrategyMatch, Observation or thesis references produce an explicit
  non-positive diagnostic and no current candidate.
- `WATCH`, `NO_TRADE`, stale, unavailable and blocking-contradiction thesis
  states are visible as non-positive outcomes and never current/actionable.
- Expired and superseded opportunity/thesis versions are excluded from current
  results.
- `OpportunityStatus.ACTIVE` is returned as lifecycle state and is not mapped to
  account authorization or trade readiness.
- Canonical market/provider/product identity and passive run/execution
  provenance are preserved.
- Repeated equivalent detections remain represented by the existing versioning
  and deduplication behavior.
- Filters, page bounds and deterministic ordering are stable.

### Authorization and safety tests

- Read authorization prevents cross-user leakage where user-specific projection
  context exists.
- The generic `TradePlanningEngine` rejects an accountless `PASSIVE_SCAN`
  opportunity with a valid account-bound planning context.
- Account-bound opportunities continue to follow existing planning-context
  authorization and status checks.
- Passive projection does not create a TradePlan, Risk evaluation or execution
  command.

### Validation commands

Run targeted Market Intelligence tests first, then the repository-standard
validation relevant to the changed module:

```bash
./mvnw -pl market-intelligence -Dtest='*PassiveAwareness*,*TradePlanningEngine*' test
./mvnw -pl market-intelligence verify
./scripts/quality-verify.sh
git diff --check
```

The exact Maven invocation must follow the module's existing build conventions
if the repository uses a different wrapper or profile. Tests must prove actual
success; no result is assumed from an earlier Story.

## Risks

- **Accountless passive opportunity bypass:** The generic TradePlan path could
  remain permissive if only the specialized service is changed. Mitigation:
  enforce and test the invariant in `TradePlanningEngine` itself.
- **Stale or contradictory evidence presented as current:** A projection could
  accidentally filter only on `ACTIVE`. Mitigation: compose thesis state,
  validity, cutoff and required evidence diagnostics before emitting a current
  item.
- **Provider identity collapse:** Matching by instrument text could merge
  duplicate provider/product records. Mitigation: require canonical market ID
  and preserve provider/product identity from Market Data.
- **N+1 read amplification:** Loading every reference independently could make
  a bounded endpoint operationally unbounded. Mitigation: define bounded query
  methods or adapter projections and test result limits.
- **Authorization leakage:** Adding a passive endpoint through an existing
  account-scoped controller could accidentally expose user-specific data.
  Mitigation: keep market awareness account-independent and validate security
  behavior with controller tests.
- **Scope expansion:** A new aggregate, ranking engine, active escalation or
  migration could be introduced unnecessarily. Mitigation: preserve the
  additive read-only boundary and reject those changes as out of scope.

No unresolved architecture conflict remains after the approved Repository
Analysis. The accountless TradePlan gap is an implementation requirement, not a
request for a new architectural decision.

## Validation Checklist

- [ ] Only `market-intelligence` and necessary canonical documentation changed.
- [ ] No `MarketCandidate` aggregate was introduced.
- [ ] No schema migration is added unless implementation proves persistence is
      required, with renewed scope review if that occurs.
- [ ] Passive awareness is read-only and bounded.
- [ ] Candidate selection requires passive origin, valid provenance, candidate
      thesis state and current validity/freshness.
- [ ] Missing, stale, unavailable and contradictory evidence remains explicit.
- [ ] Expired and superseded records are excluded from current presentation.
- [ ] Canonical market/provider/product identity is preserved.
- [ ] `ACTIVE` is not presented as account-authorized or trade-ready.
- [ ] Generic TradePlan generation rejects accountless passive opportunities.
- [ ] Existing account-bound and manual TradePlan flows remain compatible.
- [ ] Authorization tests cover cross-user leakage boundaries.
- [ ] Projection, ordering, filtering and regression tests pass.
- [ ] Targeted Maven tests, module verification, applicable quality checks and
      `git diff --check` pass.
- [ ] No broker operation, account synchronization, AI activation, active scan
      escalation or deployment was performed.

## Recommendation

Ready for implementation

The implementation strategy is sufficiently defined, the architecture is
compatible with the approved Story and all identified acceptance boundaries have
an actionable validation path. This recommendation is technical only and does
not authorize implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
