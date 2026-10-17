# Repository Analysis - Story 0120

## Status

Repository Analysis completed after explicit human approval of Story 0120.
This document records repository evidence and the implementation recommendation.
It does not authorize implementation or create an Implementation Plan.

## Sources and Authority

- The current repository is authoritative for source code, tests, Git state, and
  runtime state.
- `docs/architecture/adr/ADR-064.md` defines Broker Service ownership of
  provider/account fee truth and historical fee-schedule acquisition.
- Story `0113` defines the existing fee evidence foundation and explicitly
  deferred provider acquisition.
- Stories `0115` and `0116` define adjacent deterministic execution and
  economic-research boundaries; neither authorizes fee acquisition.
- Official Kraken documentation:
  `https://docs.kraken.com/api/docs/rest-api/get-trade-volume`
- DevLog was queried but returned `429 insufficient_quota`; no conclusion in
  this analysis relies on unavailable DevLog context.
- Obsidian was not consulted because the repository and ADRs provide the
  authoritative context for this Story.

## Git State

- Branch: `main`
- `HEAD`: `fa661ed323301d06b06d199b1a4479091565acde`
- `origin/main`: `fa661ed323301d06b06d199b1a4479091565acde`
- `git fetch origin`: completed successfully.
- The only current worktree addition is the new Story 0120 artifact.
- No application files, migrations, or tests have been modified for this Story.

## Existing Fee Evidence Foundation

### Domain model

`HistoricalFeeSchedule` already represents:

- broker-account identity;
- provider identity;
- instrument and product type;
- open or bounded effective interval;
- maker and taker rates;
- explicit `FeeRateUnit`, defaulting to `DECIMAL_FRACTION`;
- settlement asset;
- source identity;
- `observedAt` and `knownAt` timestamps;
- evidence origin;
- normalization version;
- deterministic schedule fingerprint.

The constructor enforces non-null identity and provenance, non-negative fee
rates, `effectiveFrom < effectiveTo` when an end exists, and
`observedAt <= knownAt`.

The model can represent forward-only evidence by setting `effectiveFrom` to the
first legitimate local observation boundary and leaving `effectiveTo` open
until a later distinct schedule is observed. That boundary must not be
presented as Kraken's historical effective time.

### Persistence and temporal reads

`HistoricalFeeScheduleRepository.append`:

- recomputes and validates the canonical fingerprint;
- treats an identical fingerprint as idempotent;
- rejects conflicting payloads under an existing fingerprint;
- rejects overlapping captured schedules in application code;
- persists through PostgreSQL `INSERT ... ON CONFLICT` behavior or H2 merge
  behavior used by tests.

`findEligible` requires provider, broker account, product, instrument,
execution time, and research cutoff. It enforces:

```text
effective_from <= execution_time
(effective_to is null OR execution_time < effective_to)
known_at <= research_cutoff
origin = CAPTURED
```

The schema and migration history are:

- `V3__historical_fee_schedule.sql`: canonical table, fingerprint uniqueness,
  temporal checks, and lookup index;
- `V4__prevent_overlapping_fee_schedules.sql`: PostgreSQL exclusion constraint
  for overlapping captured schedules;
- `V5__historical_fee_rate_unit.sql`: explicit `DECIMAL_FRACTION` rate-unit
  constraint.

### Existing tests

`HistoricalFeeScheduleRepositoryTest` covers captured and backfill selection,
strict temporal eligibility, duplicate identity, conflicting fingerprint
payloads, and overlapping captured schedules. The foundation has no provider
mapping, scheduled acquisition, or live fee-capture tests because that work was
deferred by Story 0113.

## Existing Kraken Integration

The active provider architecture is:

```text
KrakenCapabilities
    -> KrakenProviderClient
    -> KrakenRestProviderClient
    -> signed Kraken private endpoint
```

`KrakenProviderClient` currently exposes generic `privatePost` and `publicGet`
operations. `KrakenCapabilities` implements authentication, account, position,
order, execution, reconciliation, technical, and margin capabilities, but no
fee-schedule capability.

`KrakenRestProviderClient.privatePost` already provides the required shared
transport behavior:

- nonce generation;
- request signing through `KrakenRequestSigner`;
- credential material supplied by `KrakenCredentialSession`;
- JSON parsing;
- provider error translation;
- authentication, authorization, rate-limit, availability, protocol, and
  unknown-error classification.

The current Broker Service application does not enable Spring scheduling, and no
fee-capture scheduler or configuration properties exist. Existing scheduled
passive-capture conventions are implemented in Market Data with
`@EnableScheduling`, `@Scheduled`, `@Profile("!test")`, and disabled-by-default
capture properties. Broker Service would need a bounded equivalent if this
Story reaches implementation.

## Provider Investigation

Official Kraken documentation confirms that authenticated REST
`/0/private/TradeVolume`:

- returns 30-day USD trading volume and the resulting current fee schedule;
- requires the `Funds permissions - Query` API permission;
- accepts structured pair requests with an asset class;
- supports non-forex classes such as `derivatives`;
- can request `PF_XBTUSD` with `aclass=derivatives`;
- returns taker fees in `fees` and maker fees in `fees_maker` when applicable;
- returns percentage strings and tier information;
- can return a full current tier schedule when `fee_schedule=true`;
- identifies active tiers and volume inputs in the response.

The documented response does not contain a historical effective timestamp or
provider-defined effective interval for the returned fee schedule. The endpoint
therefore cannot reconstruct when a prior tier became effective and cannot
justify backdating a current response.

The provider semantics are sufficient for a forward observation clock if the
normalizer records the local observation boundary as the earliest defensible
eligibility boundary. They are not sufficient for historical recovery.

Provider classification:

```text
FORWARD_CAPTURE_ONLY
```

Architecture decision recommendation:

```text
IMPLEMENT
```

This recommendation is limited to forward-only capture. It does not authorize
historical backfill, current-state fallback for earlier executions, or an
effective-time claim that Kraken does not provide.

## Runtime Evidence

The running Broker Service and PostgreSQL database were inspected without reset,
truncation, or data rewrite.

Flyway history in `broker_service`:

```text
V1 broker credentials
V2 broker risk snapshots
V3 historical fee schedule
V4 prevent overlapping fee schedules
V5 historical fee rate unit
```

Current database evidence:

- `historical_fee_schedule` exists;
- the table contains `0` rows;
- the table has the expected 17 columns, including rate unit, provenance,
  observed/known timestamps, and fingerprint;
- `broker-service` is running from the current container image and remains
  operational;
- no fee-capture scheduler has produced evidence.

The empty table is expected from the existing deferred acquisition boundary; it
is not evidence that the schema or persistence foundation is missing.

## Gaps and Risks

1. **No canonical fee capability:** the provider-neutral capability vocabulary
   has no fee-schedule acquisition port, so the application boundary must be
   chosen consistently with existing Broker Service ownership.
2. **No scheduler:** Broker Service does not currently enable scheduling or have
   a passive-capture lifecycle.
3. **Current-only provider semantics:** Kraken does not document a historical
   effective timestamp for `TradeVolume`; the implementation must use a local
   forward boundary and must not imply historical recovery.
4. **Account scope:** capture requires a connected broker account and stored
   credentials with the required Kraken permission. The capture path must remain
   account-scoped and must not use global environment credentials as a silent
   substitute for stored account identity.
5. **Tier changes:** a changed response must close the previous open schedule at
   the new observation boundary and append the new schedule atomically enough to
   preserve the no-overlap invariant. An identical response must remain
   idempotent.
6. **Product and settlement semantics:** the provider response must be mapped
   to the canonical product type and settlement asset without leaking Kraken
   DTOs or silently accepting an unresolved product mapping.
7. **Credential permission:** existing credential validation policies may need to
   confirm the funds-query permission before activation; this must not weaken
   existing credential security behavior.

## Recommended Implementation Boundary

If the human approves this Repository Analysis and an Implementation Plan is
not required beyond the accepted Story and repository conventions, the smallest
implementation should be limited to:

1. Add the minimal provider-specific TradeVolume request/response mapping under
   the Kraken infrastructure adapter.
2. Add a provider-neutral fee-schedule acquisition capability or application
   port inside Broker Service.
3. Normalize only validated current maker/taker tiers for the configured
   bounded instrument and account.
4. Use the local observation boundary as `effectiveFrom` for forward-only
   captured schedules; close a prior open schedule only when a distinct valid
   schedule is observed.
5. Add bounded, configurable, disabled-by-default scheduling using stored
   account credentials.
6. Preserve repository fingerprint idempotence and no-overlap protections.
7. Add provider mapping, normalization, temporal, failure, scheduling,
   persistence, and runtime regression tests.
8. Validate the existing runtime database and confirm at least one truthful
   canonical row before claiming runtime success.

No public API, Trading Core integration, economic-result change, historical
backfill, or Market Intelligence change should be included.

## Decision Gate

The Repository Analysis recommendation is:

```text
Provider classification: FORWARD_CAPTURE_ONLY
Architecture decision: IMPLEMENT
```

Human approval of this Repository Analysis is required before producing an
Implementation Plan or modifying application code.
