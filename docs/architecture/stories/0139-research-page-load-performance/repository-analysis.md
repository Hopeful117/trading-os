# Repository Analysis - Story 0139

## Repository Baseline

- Branch: `main`
- HEAD: `1708bac06bb91e0d9bbb3e8c23f8a48a18446db2`
- The working tree contains pre-existing untracked architecture reports. They
  were not modified or removed.
- Story 0139 is newly created and remains `Draft`.
- No application source change was present for this Story before this analysis.
- DevLog context was requested and unavailable because the provider returned an
  insufficient-quota error. Repository evidence remains authoritative.

## Story and Historical Context

Story 0133 established the current separation between Acquisition Integrity,
Experimental Readiness, and Results. It explicitly requires Market Intelligence
to own the deterministic integrity read model and requires the frontend to
project authoritative backend states rather than calculate semantics.

Story 0111 established the continuous research lifecycle and internal readiness
surface. Story 0131 established versioned research sample binding. These
boundaries must remain unchanged while improving page load performance.

Relevant architectural constraints are:

- ADR-022: cache ownership, dependency-aware validity, and freshness semantics;
  a TTL alone is not a business-validity rule and Market Intelligence must not
  become an owner of data belonging to another service.
- ADR-055: bounded latency, provider-load control, and shareable/coalesced
  readiness behavior for Market Data access.
- Existing research and evidence decisions: deterministic projections,
  immutable evidence, explicit cutoff semantics, and fail-closed handling of
  unknown or unavailable authoritative data.

## Current Frontend Request Flow

`ResearchOverview` builds one shared Observable with `shareReplay` and triggers
one refresh sequence from the component. The current sequence is:

```text
refresh
  -> GET /v1/intelligence/research/readiness
      -> GET /v1/intelligence/research/integrity
          -> ready view with both projections
```

The integrity request is nested inside the readiness response handler. Therefore
the two independent projections are not requested concurrently.

The current frontend already has useful safety behavior:

- readiness is rendered before integrity completes;
- integrity failure leaves readiness visible and marks integrity as unknown;
- readiness failure produces a retryable error state;
- refresh creates one new readiness request;
- `shareReplay` prevents duplicate subscriptions from duplicating the active
  request sequence while the view is subscribed.

The relevant files are:

- `trading-os-web/src/app/features/research/research-overview.ts`
- `trading-os-web/src/app/core/services/research.service.ts`
- `trading-os-web/src/app/features/research/research-overview.html`
- `trading-os-web/src/app/features/research/research-overview.spec.ts`
- `trading-os-web/src/app/core/models/research-readiness.model.ts`

The first implementation candidate is consequently a narrow frontend
orchestration change, not a contract change: start the two requests together,
preserve progressive rendering, and retain the current independent fallback
semantics.

## Gateway and API Boundary

The public endpoints are separate and already routed to Market Intelligence:

- `GET /api/v1/intelligence/research/readiness`
- `GET /api/v1/intelligence/research/integrity`

Gateway authentication and actor propagation already cover the intelligence
route. Existing Gateway tests verify both route selection and authenticated
forwarding. No Gateway change is required for Step 1.

Market Intelligence also exposes separate internal readiness and integrity
endpoints. No current evidence requires a combined endpoint for the first step.

## Readiness Cost Profile

`ResearchReadinessService.current()` has a synchronized in-memory cache with a
configured TTL. On a cache miss it:

1. validates research lifecycle properties;
2. loads all configured feature snapshots over the full available history;
3. loads all outcomes for configured markets and filters configured horizons in
   application code;
4. calls historical OHLC per configured market sequentially to determine latest
   evidence timestamps;
5. derives signal and regime information from feature snapshots and persisted
   artifacts;
6. performs replay and historical fee checks using bounded executor concurrency,
   deduplicated keys, a shared remote-check budget, and fail-closed exception
   handling;
7. caches the complete projection.

The replay and fee concurrency/budget implementation is an existing safety
boundary. Step 2 may evaluate the sequential OHLC lookups, but must not remove
or weaken replay/fee limits, retry safety, or fail-closed behavior.

The largest current local read risks are broad historical loading, domain
deserialization, and application-side filtering. The largest remote risk is
per-market OHLC fan-out. Both require measurements before modification.

## Integrity Cost Profile

`ResearchIntegrityService.current()` has its own synchronized in-memory cache and
TTL. On a cache miss it:

1. validates lifecycle configuration;
2. performs one latest-snapshot lookup per configured market;
3. loads feature snapshots in a bounded maturation window;
4. loads outcomes in the same bounded cutoff window;
5. groups outcomes and derives maturation, overdue, and timestamp-coherence
   information in memory;
6. caches the integrity projection.

The current integrity implementation is already bounded by maximum outcome
horizon plus observation cadence for maturation. Its per-market latest-snapshot
lookups and the separate snapshot/outcome reads are candidates for measurement,
but not automatically defects.

## Persistence Query Assessment

The current repositories expose:

- broad or market-scoped feature snapshot population queries with cutoff,
  projection-version, and evidence-policy filters;
- latest feature snapshot by market;
- all outcomes by market;
- outcomes in a market/cutoff range.

The Spring Data methods return complete entities which are then converted to
domain objects. The repository interfaces do not currently expose count-only,
grouped, latest-per-market, or status aggregation projections.

Step 3 is therefore technically plausible, but requires evidence about:

- actual row counts and payload sizes;
- database execution plans and index usage;
- deserialization cost;
- whether counts and grouping preserve the exact existing domain semantics;
- whether a projection would duplicate domain rules or introduce a second
  authority.

The safe default is to add targeted read methods only after profiling and to
keep semantic composition in Market Intelligence.

## Cache and Projection Assessment

Readiness and integrity currently have independent process-local caches. This
avoids a new shared cache dependency but permits duplicate underlying reads when
both caches expire around the same time.

A combined backend projection could reduce duplicate reads and network overhead,
but it would introduce a new coupling between two currently separate read
models. It may also make one slow or failed computation delay the other unless
the response and computation model explicitly preserve independent availability.

A precomputed or materialized view could provide the best response latency, but
requires explicit freshness, invalidation, ownership, recovery, and stale-data
semantics. ADR-022 rejects TTL as the sole business-validity rule and does not
select a cache technology. Any durable shared projection or cache authority
therefore requires an architectural decision before implementation.

Step 4 should remain conditional and should not be treated as the default
implementation outcome.

## Measurement Plan

The Story requires a baseline before choosing implementation scope. The baseline
must distinguish cold and warm cache behavior and record:

- browser time to first useful readiness content;
- browser time to complete readiness plus integrity content;
- request ordering, count, duration, status, and payload size;
- backend readiness and integrity duration separately;
- database query count, row count, and materialized payload volume where
  instrumentation permits;
- remote OHLC, replay, and fee call count and duration;
- cache hit/miss and expiration behavior;
- partial dependency failure and retry behavior.

The baseline should be captured before and after each accepted step. A change
that improves total latency while increasing unsafe provider load, duplicate
requests, stale ambiguity, or semantic drift must be rejected.

## Recommended Decision

**A - STAGED IMPLEMENTATION WITH CONDITIONAL BACKEND FOLLOW-UPS**

Recommended sequence:

1. Establish the baseline and implement only frontend concurrency if the baseline
   confirms that sequential requests are material. This is the smallest change
   and does not require an API or architecture change.
2. Measure backend remote and local costs separately. Evaluate bounded OHLC
   concurrency only if it is material and can use explicit limits consistent with
   existing provider-load controls.
3. Evaluate targeted repository projections for broad snapshot/outcome reads. Do
   not implement a query rewrite without result-equivalence and query-plan
   evidence.
4. Prepare an ADR only if measurements justify a shared backend projection,
   precomputation, or new cache authority. Otherwise defer Step 4.

This recommendation does not authorize source changes. It identifies the next
permitted artifact as an Implementation Plan after human approval of this
Repository Analysis.

## Risks and Open Decisions

- No reproducible browser timing baseline has been captured in this analysis.
- Runtime readiness cost varies with warm/cold service and database state.
- Parallel OHLC calls could reduce latency while increasing Market Data load or
  provider rate-limit exposure.
- A combined endpoint could simplify frontend loading while coupling independent
  failure and freshness semantics.
- Targeted database projections may improve performance but risk duplicating
  readiness domain logic.
- Precomputed data may improve latency but must expose freshness and recovery
  state rather than silently serving stale readiness.
- DevLog historical context remains unavailable due to provider quota exhaustion.

## Validation Status

- Repository inspection completed.
- Story and relevant source paths verified.
- Gateway route and frontend request tests identified.
- No application tests were run because this stage is Repository Analysis only.
- No source implementation change was made.
- `git diff --check` had passed for the Story artifact before this analysis.

## Approval Gate

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan or source changes.

The next permitted action is:

`Approve and continue Story 0139`
