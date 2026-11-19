# Implementation Plan - Story 0247-02

## Plan Status

Draft - pending human approval. This plan does not authorize implementation until explicitly
approved.

## Objective

Add a deterministic, immutable opportunity-outcome contract in Market Intelligence. The contract
will capture lifecycle and subsequent-market observations for one exact opportunity version while
keeping `ObservedMarketOutcome` objective, market-scoped, and independent from execution results.

## Proposed Design

### 1. Separate opportunity outcome aggregate

Add a new domain package under `domain/opportunityoutcome` with:

- `OpportunityOutcome` immutable aggregate;
- `OpportunityOutcomeStatus` with explicit states for:
  - `EXPIRED`;
  - `INVALIDATED`;
  - `SUPERSEDED`;
  - `MARKET_MOVEMENT_OBSERVED`;
  - `UNKNOWN`;
  - `NON_EVALUABLE`;
- `OpportunityOutcomeIdentity` for exact semantic selection;
- a versioned fingerprint factory for deterministic identity.

The aggregate will retain, at minimum:

- exact `OpportunityId` and `OpportunityVersion`;
- opportunity evidence/setup fingerprint;
- market identity;
- evaluation cutoff and horizon;
- maturity timestamp;
- outcome status and bounded reason code;
- optional exact `ObservedMarketOutcomeIdentity` when future market evidence is available;
- outcome definition version, result fingerprint, computed-at timestamp, and source execution
  reference where applicable.

No execution, trade, risk, commission, funding, settlement, or P&L fields will be introduced.

### 2. Deterministic evaluation service

Add `OpportunityOutcomeEvaluationService` in the application layer with a request containing:

- exact opportunity ID and version;
- evaluation cutoff;
- horizon;
- optional analysis execution context;
- optional historical evidence selection when the existing market-outcome source requires it.

The service will:

1. Load the exact opportunity version and fail closed when it is missing.
2. Verify that the opportunity evidence and timestamps are not after the requested cutoff.
3. Resolve lifecycle facts without mutating the opportunity:
   - an already persisted `EXPIRED` version maps to `EXPIRED`;
   - a newer immutable version maps the older version to `SUPERSEDED`;
   - deterministic blocking evidence diagnostics map to `INVALIDATED` only when the existing
     application contract can establish that fact without look-ahead;
   - unresolved lifecycle evidence remains `UNKNOWN`.
4. For a still-evaluable opportunity at or after maturity, delegate objective future-market
   evaluation to the existing `MarketOutcomeEvaluationService`.
5. Map `PENDING`, `INCOMPLETE`, and `UNAVAILABLE` market evidence to `NON_EVALUABLE` or `UNKNOWN`
   according to an explicit, tested policy rather than inferring movement.
6. Persist the opportunity outcome with `saveIfAbsent`, returning the existing fact on an identical
   retry and rejecting conflicting semantic content for the same fingerprint.

The service will not transition opportunities, create scans, create trade plans, or call execution
or risk services.

### 3. Persistence

Add a PostgreSQL migration and persistence adapter for an append-only
`opportunity_outcomes` table. The table will include:

- generated operational row identity;
- exact opportunity ID/version and market ID;
- evaluation cutoff, horizon, and maturity;
- status and reason;
- source opportunity/evidence fingerprint;
- optional objective market-outcome identity fields;
- outcome definition and result fingerprints;
- computed-at and analysis-execution provenance.

The result fingerprint will be unique. Lookup will support exact semantic identity and bounded
queries by opportunity, cutoff, horizon, and status. No database ordering or `computedAt` rule will
select between immutable corrections.

### 4. Internal application boundary

Add an internal Market Intelligence evaluation endpoint only if the existing orchestration needs a
service boundary for the request. It will use `ResponseEntity`, remain under `/internal/v1`, and
remain unavailable to frontend, broker, agent, and execution clients. If focused repository
inspection shows that an existing lifecycle caller is sufficient, prefer the application service
without adding a redundant endpoint.

### 5. Tests

Add focused tests for:

- every opportunity-outcome status and domain invariant;
- exact opportunity version lookup and missing-version fail-closed behavior;
- cutoff equality, pre-maturity, maturity, and post-maturity cases;
- expiration and supersession detection;
- invalidation and unknown diagnostics;
- available, incomplete, unavailable, stale, gapped, and post-cutoff market evidence;
- no look-ahead into the original opportunity evidence or fingerprint;
- deterministic fingerprints and immutable corrections;
- sequential and concurrent duplicate persistence;
- JPA round-trip and migration behavior;
- separation from executed-trade fields and `ObservedMarketOutcome` semantics.

## Likely Files

The exact file list will be confirmed during implementation without broad refactoring.

- `market-intelligence/src/main/java/.../domain/opportunityoutcome/`
- `market-intelligence/src/main/java/.../application/opportunityoutcome/`
- `market-intelligence/src/main/java/.../application/port/`
- `market-intelligence/src/main/java/.../adapter/persistence/`
- `market-intelligence/src/main/java/.../adapter/web/` only if the internal boundary is required
- `market-intelligence/src/main/resources/db/migration/`
- focused domain, application, persistence, and integration tests

## Explicit Non-Changes

- Do not alter the meaning or schema of `ObservedMarketOutcome`.
- Do not alter `Trading Core`, `Risk Domain`, Broker Service, execution, settlement, or frontend.
- Do not add automatic trading, ActiveScan creation, ranking, probability, or profitability claims.
- Do not add global backfill or a new microservice.
- Do not silently change existing opportunity lifecycle transitions.

## Validation Commands

Targeted validation during implementation:

```bash
(cd market-intelligence && ./../trading-core/mvnw -q -Dtest=<focused-tests> test)
git diff --check
```

Final validation:

```bash
(cd market-intelligence && ./../trading-core/mvnw -q verify)
```

The implementation report will record any runtime or infrastructure limitation separately from
passing local tests.

## Risks Requiring Review

- The exact boundary between `INVALIDATED`, `UNKNOWN`, and `NON_EVALUABLE` must remain deterministic
  and must not infer facts from missing evidence.
- A correction of the objective market outcome must create a new opportunity-outcome fingerprint,
  not mutate the prior fact.
- Lifecycle state and market movement are separate dimensions; neither may be presented as trade
  performance.
- Any future scheduler or user-facing exposure is outside this Story unless separately approved.
