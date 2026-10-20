# Implementation Plan - Story 0133

## Approval Context

This plan follows the approved Repository Analysis and implements only the
rigorous subset identified there. It does not change frozen S1 semantics,
Research Readiness gates, outcome definitions, or any experimental result
interpretation.

## 1. Backend Read Model

Add a Market Intelligence application read model dedicated to acquisition
integrity, with no outcome-value fields. The model will contain:

- evaluation timestamp;
- enabled/configuration state;
- configured market count;
- per-market acquisition freshness state and latest snapshot cutoff;
- bounded maturation counts by configured horizon;
- overdue maturation count;
- latest successful outcome computation timestamp;
- timestamp anomaly count and non-sensitive warning identifiers;
- deterministic overall integrity state only when all required inputs are known.

Use explicit status values for `HEALTHY`, `DEGRADED`, `STALE`, `NO_DATA_YET`,
and `UNKNOWN` where applicable. The aggregate state must fail closed:
configuration errors, repository failures, or an unknown critical dimension
cannot produce `HEALTHY`.

Do not include outcome prices, returns, labels, or any performance-derived
field in the contract.

## 2. Bounded Persistence Queries

Extend the existing repository ports and Spring Data adapters without adding a
table or changing existing persistence semantics.

### Latest feature snapshot

Add an index-backed latest-per-market lookup constrained by projection version
and evidence policy. The adapter will resolve one latest row per configured
market rather than loading the complete historical population.

### Maturation window

Add a market/cutoff-range outcome lookup using the existing
`(market_id, evaluation_cutoff, horizon_seconds)` index. The integrity service
will query only the interval needed to cover the maximum configured horizon and
one observation cadence around the current evaluation time.

### Snapshot maturation candidates

Reuse the existing bounded population query for snapshots in the same time
window. Match candidate snapshots to outcomes by market, evaluation cutoff, and
configured horizon. Count existence and status only.

No unbounded `findByMarkets` or full historical population read may be added to
the integrity path.

## 3. Freshness Semantics

For each configured market:

1. Derive the expected cutoff from the existing observation cadence and lifecycle
   fixed-delay semantics.
2. Read the latest persisted FeatureSnapshot cutoff.
3. Classify the market as:
   - `HEALTHY` when the cutoff meets the expected cutoff;
   - `DEGRADED` when it is behind by at most one cadence;
   - `STALE` when it is older than that bounded grace;
   - `NO_DATA_YET` when no snapshot exists;
   - `UNKNOWN` when configuration or repository access fails.

The aggregate acquisition state will preserve the worst meaningful state and
will never hide `UNKNOWN` or `NO_DATA_YET` behind a healthy result.

## 4. Outcome Maturation Semantics

For each configured horizon and bounded candidate snapshot:

- a not-yet-matured candidate remains pending and is not an anomaly;
- a matured candidate without a corresponding outcome is overdue;
- a matured candidate with `AVAILABLE`, `INCOMPLETE`, or `UNAVAILABLE` status
  is counted by status;
- the read model exposes timing and existence metadata only.

The service will not read or serialize outcome numerical values. It will not
interpret any status as positive or negative strategy evidence.

## 5. Timestamp Coherence

Implement only deterministic checks supported by current domain semantics:

- outcome maturity must equal evaluation cutoff plus configured horizon;
- computed-at must not precede the originating evaluation cutoff;
- observed feature cutoffs must not be future-dated relative to evaluation time.

Record counts and stable warning identifiers. Do not introduce a generic data
quality framework or infer chronology from provider fields not represented in
the current contract.

## 6. Replay and Fee Boundaries

Do not add new replay or fee health calculations. Existing S1 readiness remains
the authority for replay and fee coverage gates. The integrity response will
state that those operational dimensions are not independently assessed rather
than duplicating or reinterpreting readiness data.

## 7. Service and Cache

Add an `ResearchIntegrityService` owned by Market Intelligence. Reuse the
existing readiness cache duration and synchronized cached-projection pattern,
with a separate cache entry so integrity computation cannot change readiness
semantics.

Validate lifecycle configuration before computation. Convert repository or
required dependency failures into an `UNKNOWN` read model with explicit warning
metadata rather than a healthy response.

## 8. API Contract

Add:

- authenticated public endpoint:
  `GET /api/v1/intelligence/research/integrity`;
- matching internal endpoint:
  `GET /internal/v1/research/integrity`.

Use `ResponseEntity` and the existing controller conventions. The existing
Gateway `/api/v1/intelligence/**` route should cover the public endpoint; add a
focused route contract test only if the current route predicate does not.

Do not modify the existing `/readiness` response contract.

## 9. Frontend Projection

Extend the existing Research service with `getIntegrity()` and add typed model
interfaces matching the backend response.

Update Research Overview to request readiness and integrity together while
keeping independent loading/error projection. If the integrity request fails,
render an explicit `UNKNOWN`/unavailable integrity state and retain the
readiness projection when available.

Add a compact Acquisition Integrity panel containing:

- overall status;
- latest evidence summary;
- maturation warning counts;
- timestamp warning count;
- explicit unknown/degraded explanations.

Keep the existing Readiness gate panel and Results-unavailable boundary
visually separate. Angular may format values and labels only; it must not
classify statuses or derive integrity from raw counts.

Keep refresh manual, matching the current Research Overview behavior. Do not
introduce high-frequency polling.

## 10. Tests

### Market Intelligence

Add focused service tests for:

- fresh evidence;
- degraded and stale evidence boundaries;
- no data;
- unknown configuration/repository failure;
- bounded maturation with pending, available, incomplete, unavailable, and
  overdue states;
- timestamp anomaly detection;
- fail-closed aggregate state;
- cache reuse and expiry;
- absence of unbounded repository calls.

Add repository/controller contract tests for bounded query delegation and both
API boundaries.

### Angular

Extend Research Overview tests for:

- healthy integrity projection;
- degraded/stale and unknown states;
- maturation warnings;
- integrity request failure while readiness remains separate;
- results remaining unavailable/not evaluated;
- one request per endpoint on refresh.

No test will calculate or assert S1 performance.

## 11. Documentation and ADR Decision

Update the Story implementation artifacts with the final semantics and
validation results. No ADR is planned: this is a read-only sibling projection
within the existing Market Intelligence ownership boundary, with no persisted
truth or new cross-service responsibility.

If implementation reveals that a new durable acquisition ledger or a new
ownership boundary is required, stop and request architectural review instead
of extending this plan silently.

## 12. Validation

Run:

- focused Market Intelligence research tests;
- affected Market Intelligence security/controller tests;
- Market Intelligence Maven verification/build;
- focused Angular Research tests;
- complete Angular test suite;
- Angular production build;
- Prettier/format checks where configured;
- `git diff --check`;
- bounded query and complete diff review.

Perform read-only runtime validation only if the stack and authenticated
environment are available. Do not alter lifecycle configuration or research
evidence to force a status.

## 13. Expected Files

Likely affected areas, subject to implementation evidence:

- `market-intelligence` research application model/service;
- feature snapshot and observed outcome repository ports/adapters;
- research web controllers and tests;
- Market Intelligence application properties only if cache configuration must be
  shared explicitly;
- Angular Research model/service/overview/template/style/tests;
- Gateway tests only if route coverage requires it;
- Story implementation report and final documentation artifacts.

No database migration, backend service boundary change, frozen research
artifact modification, or performance/result surface is planned.
