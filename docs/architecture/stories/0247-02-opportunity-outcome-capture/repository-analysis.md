# Repository Analysis - Story 0247-02

## Analysis Status

Draft analysis for human approval. It does not authorize implementation.

## Scope Understanding

Story `0247-02` is the second dependency-ordered Story of Epic `0247`. Its purpose is to create a
deterministic opportunity-outcome boundary after detection while preserving the existing separation
between objective market movement and executed-trade results.

The Epic acceptance criteria require a new distinction that is not currently represented by either
the opportunity lifecycle or the generic objective-market outcome contract.

## Current Repository State

### Existing objective market outcomes

`market-intelligence` already contains:

- `domain/outcome/ObservedMarketOutcome`;
- `domain/outcome/ObservedMarketOutcomeStatus`;
- `domain/outcome/MarketOutcomeCalculator`;
- `application/outcome/MarketOutcomeEvaluationService`;
- `application/port/ObservedMarketOutcomeRepository`;
- `JpaObservedMarketOutcomeRepository` and `V14__objective_future_market_outcomes.sql`;
- `InternalMarketOutcomeController`.

This contract is keyed by market, evaluation cutoff, horizon, definition version, and outcome
fingerprint. It records objective future market facts and currently supports `PENDING`,
`AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE` semantics. It is already idempotent through
`saveIfAbsent` and fingerprint conflict detection.

### Existing opportunity model

`domain/opportunity/TradingOpportunity` contains the immutable opportunity version, market identity,
direction, timeframe, validity window, strategy match reference, observations, market thesis,
analysis execution, and setup snapshot.

`OpportunityStatus` currently contains:

```text
DETECTED, ANALYZED, ACTIVE, CONSUMED, EXPIRED
```

`OpportunityLifecyclePolicy`, `DefaultOpportunityRegistry`, and
`OpportunityExpirationDriver` persist expiration as a lifecycle transition. They do not persist an
outcome observation describing why or how the opportunity became invalid or expired.

`PassiveCandidateEvaluator` detects diagnostic conditions including supersession, thesis
supersession, evidence expiry, contradictions, reference mismatches, and unavailable evidence. Those
diagnostics are projections and are not currently an immutable opportunity-outcome fact.

## Architectural Boundaries

- Do not add opportunity direction, strategy identity, trade semantics, or P&L to
  `ObservedMarketOutcome`.
- Do not infer executed-trade performance from subsequent market movement.
- Preserve the original opportunity and its evidence as immutable historical facts.
- Use an explicit cutoff and horizon for any future-market observation.
- Treat missing or post-cutoff evidence as non-evaluable, not as a positive or negative outcome.
- A changed definition or corrected source evidence must create a new immutable fingerprint.
- Any persistence identity must be separate from the portable semantic identity.

## Likely Affected Areas

These are candidates for the Implementation Plan, not an approved file list:

- `market-intelligence/src/main/java/.../domain/opportunity/`
- `market-intelligence/src/main/java/.../domain/outcome/`
- `market-intelligence/src/main/java/.../application/opportunity/`
- `market-intelligence/src/main/java/.../application/outcome/`
- `market-intelligence/src/main/java/.../application/port/`
- `market-intelligence/src/main/java/.../adapter/persistence/`
- `market-intelligence/src/main/resources/db/migration/`
- focused domain, application, persistence, and integration tests.

No change to Trading Core, Risk, Broker Service, execution, frontend, Gateway, or a new service is
currently justified.

## Main Design Question

The existing generic market-outcome service is reusable for subsequent objective movement, but the
opportunity outcome must likely be a separate aggregate or projection that references:

- exact opportunity ID and version;
- source cutoff and evaluation horizon;
- immutable source opportunity/evidence fingerprint;
- the objective market-outcome identity, when available;
- an explicit opportunity-outcome state and reason;
- evaluation definition version and result fingerprint.

The Story must decide whether expiration, invalidation, and supersession are captured as lifecycle
events, as an immutable outcome fact, or both. That decision must avoid treating a lifecycle status
transition as a market-performance label.

## Risks and Gaps

- The Epic does not define the exact invalidation rules or whether they are observed at detection
  time, at evaluation time, or both.
- The Epic does not define the required horizon set or whether a horizon is one fixed duration or a
  configured collection.
- `ObservedMarketOutcome` currently has no direct opportunity reference; blindly reusing it would
  violate the existing architecture boundary.
- Current supersession and invalidation signals are projection diagnostics rather than persisted
  immutable facts.
- Concurrent duplicate behavior must be specified for both same-fingerprint retries and conflicting
  semantic content under the same idempotency key.
- Look-ahead protection must cover source selection, future observation range, and outcome-state
  derivation.

## Validation Plan Candidates

- fixed-clock tests exactly at `evaluationCutoff`, `evaluationCutoff + horizon`, and just before or
  after maturity;
- duplicate sequential and concurrent persistence tests;
- immutable correction/fingerprint tests;
- expiration, invalidation, supersession, movement, unknown, and non-evaluable fixtures;
- unavailable, stale, missing, post-cutoff, and gapped future evidence fixtures;
- tests proving trade/execution fields cannot enter the market-outcome or opportunity-outcome
  contracts;
- JPA round-trip and migration validation;
- full `mvn -q verify` for `market-intelligence` after implementation.

## DevLog Context

DevLog was queried for prior decisions and implementation history. The returned context was
completed but truncated (`candidateCount=1469`, `selectedCount=60`, `truncated=true`) and classified
with low confidence. It did not provide reliable Story-specific findings. The current repository,
Epic, and accepted ADRs remain authoritative.

## Recommendation

The Story is suitable for planning, but implementation should not begin until the human approves
this Repository Analysis and resolves the exact outcome-state, horizon, and lifecycle-event
semantics. The next workflow artifact is an Implementation Plan, not code.
