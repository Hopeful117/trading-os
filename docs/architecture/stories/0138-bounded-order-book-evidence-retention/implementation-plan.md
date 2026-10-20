# Implementation Plan - Story 0138

## Status

Approved by the human engineer before implementation.

This plan implements approved Story 0138 and accepted ADR-067. It does not
activate destructive retirement for the existing Order Book evidence population.

## Implementation Boundary

The primary implementation remains inside `market-data`, which owns the replay
artifact, dependency registry, retention holds, retirement selection, and physical
evidence lifecycle.

`market-intelligence` and `trading-core` receive only the internal contracts needed
to create or release semantic dependencies. They do not access Market Data tables
directly and do not delete evidence.

No public API, Gateway route, Frontend behavior, Broker, Risk, authentication, or
new storage platform is included.

## Decisions Fixed By This Plan

### 1. Reproducibility Boundary

An immutable promoted replay artifact is the reproducibility boundary for a frozen
research or historical replay dependency. Raw evidence may become eligible for
retirement only after every dependency and hold is released.

The artifact must retain:

- market and instrument identity;
- provider and provider-symbol provenance;
- requested depth and normalization version;
- evaluation cutoff;
- selected sequence identity;
- trusted starting snapshot/checkpoint identity;
- source ordinal range and `knownAt` boundaries;
- source evidence fingerprints;
- checksum/trust status;
- artifact schema/version and promotion reason.

### 2. Dependency Registry Ownership

The V1 dependency registry is owned by Market Data. Other services use an internal
application contract to register, promote, or release a dependency. They do not
write directly to the Market Data database.

Unknown or missing dependency metadata is protected by default. Absence of a
dependency row is never interpreted as release.

### 3. Destructive Operation Safety

Retirement is disabled by default for existing production evidence. The first
implementation supports dry-run selection and controlled mutation only for
explicitly selected test or human-approved ranges.

Historical backfill and broad cleanup require a separate migration decision after
the lifecycle is proven and PostgreSQL operational effects are measured.

## Production Changes

### 1. Define Promotion Domain Contracts

- Add immutable value objects for replay promotion requests, replay artifact
  identity, source evidence range, and provenance.
- Map `OrderBookReplayResult` into the promotion contract without reimplementing
  replay reconstruction.
- Reject unavailable, invalid, incomplete, cross-sequence, and look-ahead results.
- Calculate a deterministic artifact fingerprint from semantic request parameters,
  replay cutoff, selected sequence, source fingerprints, and normalization version.
- Keep generated database IDs separate from portable semantic identity.

### 2. Add Immutable Promotion Persistence

- Add a Flyway migration for promoted replay artifacts.
- Store the complete provenance required by ADR-067.
- Enforce uniqueness for the semantic artifact identity/fingerprint.
- Preserve immutable conflict behavior: identical promotion is idempotent; a
  conflicting artifact is rejected explicitly.
- Add repository methods for create, find by semantic identity, and verify source
  provenance.
- Do not mutate canonical evidence during promotion.

### 3. Add Dependency and Hold Lifecycle

- Add persistence for semantic evidence dependencies.
- Add persistence for explicit retention holds and their release metadata.
- Support states `PROTECTED`, `PROMOTED`, `RELEASED`, and `HOLD`.
- Record owner/type, semantic consumer identity, referenced artifact/source range,
  creation time, release time, and reason/provenance.
- Make transitions explicit and idempotent.
- Reject invalid transitions and preserve an audit trail.
- Keep dependency lifecycle separate from `ACTIVE`, `SEALED`, and `INVALIDATED`
  sequence status.

### 4. Add Promotion and Dependency Application Services

- Add a Market Data application service for strict replay promotion.
- Add internal service ports for dependency protection and release.
- Keep the service contract provider-neutral and independent of Kraken DTOs.
- Ensure promotion and dependency creation can be retried safely.
- Ensure a failed dependency write cannot make evidence appear releasable.
- Preserve explicit ownership boundaries for Market Intelligence and Trading Core.

### 5. Add Dry-Run Retirement Selection

- Add a retirement query/application service that selects only evidence whose
  sequence is no longer active and whose dependencies and holds are released.
- Protect evidence when metadata is missing, ambiguous, or unresolved.
- Report exact sequence, ordinal, timestamp, artifact, dependency, and hold ranges.
- Persist a retirement operation/audit record before any mutation.
- Support resumable operation status and idempotent re-execution.
- Keep physical mutation behind an explicit disabled-by-default configuration gate.

### 6. Add Controlled Retirement Mutation

- Implement physical retirement only for explicitly selected ranges after dry-run
  confirmation.
- Make the operation transactional at the selected range/operation boundary.
- Never retire active sequences, unresolved dependencies, holds, or evidence needed
  by a promoted artifact.
- Do not run this operation against the existing approximately 36 million rows as
  part of this Story.
- Capture affected ranges and final outcome in the audit record.
- Document PostgreSQL vacuum/index-bloat follow-up requirements.

### 7. Add Storage-Pressure Observability and Capture Quarantine

- Extend existing capture durability diagnostics with storage headroom and last
  successful persistence signals.
- Add explicit degraded/quarantined capture state when canonical persistence cannot
  be acknowledged.
- Ensure storage pressure never triggers implicit evidence deletion.
- Ensure strict replay reports unavailable/degraded state rather than falling back
  to live provider state.
- Expose metrics/logs using existing Market Data observability conventions.

## Expected Files and Surfaces

Expected additions or changes are limited to:

- `market-data/src/main/java/.../model/` for promotion, dependency, hold, and
  retirement domain types;
- `market-data/src/main/java/.../service/` for promotion, dependency lifecycle,
  retirement selection, and storage-pressure orchestration;
- `market-data/src/main/java/.../service/OrderBookReplayService.java` only if the
  existing replay result needs additional provenance;
- `market-data/src/main/java/.../service/OrderBookEvidenceRepository.java` for
  controlled lifecycle queries, without weakening strict replay;
- `market-data/src/main/resources/db/migration/` for additive schema migrations;
- `market-intelligence` and `trading-core` internal application ports only where
  existing research/replay lifecycles need to register or release dependencies;
- affected unit and PostgreSQL integration tests;
- configuration documentation for disabled-by-default retirement.

No direct cross-service database access is permitted. Exact class/table names may
be selected during implementation if they preserve the approved semantic contracts.

## Persistence Semantics

- Promoted artifacts are immutable and independently fingerprinted.
- Dependency and hold state transitions are explicit and auditable.
- Missing metadata protects evidence.
- Duplicate promotion, dependency registration, release, and retirement requests
  are idempotent.
- Existing evidence rows remain valid and untouched by the initial migration.
- Existing sequence lifecycle remains independent from retention lifecycle.
- Physical retirement is disabled by default and never runs implicitly under storage
  pressure.

## Test Plan

### Promotion Tests

- Promote a valid replay with complete provenance.
- Reject invalid, unavailable, incomplete, cross-sequence, and look-ahead results.
- Verify deterministic artifact identity and source fingerprint coverage.
- Verify duplicate promotion idempotency and conflicting identity rejection.
- Verify artifact immutability after creation.

### Dependency and Hold Tests

- Create, promote, release, and hold dependencies.
- Reject invalid state transitions.
- Verify unresolved, missing, and ambiguous metadata protects evidence.
- Verify sequence lifecycle state does not replace retention state.
- Verify internal callers cannot bypass the Market Data lifecycle boundary.

### Retirement Tests

- Select eligible evidence in dry-run mode.
- Exclude active sequences, unresolved dependencies, holds, and promoted ranges
  still required by frozen research.
- Verify exact range/audit output.
- Verify restart/resume and idempotency.
- Verify mutation remains disabled by default.
- Verify controlled test-range retirement only after explicit enablement.
- Verify existing evidence is not selected by accidental broad queries.

### Storage-Pressure Tests

- Report headroom and last successful persistence.
- Quarantine capture when durable persistence cannot be acknowledged.
- Preserve fail-closed replay behavior under degraded storage.
- Verify storage pressure does not trigger cleanup.

### Repository and Runtime Validation

- Market Data targeted tests and PostgreSQL integration tests.
- Affected Market Intelligence and Trading Core contract tests, if production
  adapters are added.
- Full Maven verification for every modified service.
- Migration validation against the existing Market Data database.
- Runtime dry-run against the local environment with destructive retirement off.
- `git diff --check` and complete diff review.

## Implementation Sequence

1. Define promotion, dependency, hold, and retirement domain contracts.
2. Add additive PostgreSQL migrations and immutable repositories.
3. Implement promotion validation and idempotent persistence.
4. Implement dependency/hold transitions and internal application ports.
5. Implement dry-run retirement selection and audit records with mutation disabled.
6. Add controlled test-range mutation behind explicit configuration.
7. Add storage-pressure metrics and capture quarantine behavior.
8. Run migration, integration, and runtime dry-run validation.
9. Prepare a separate historical dependency-backfill and cleanup proposal.

## Risks and Stop Conditions

- If a consumer cannot provide a stable semantic dependency identity, stop and
  request a contract decision rather than using a generated UUID alone.
- If promotion cannot preserve existing replay semantics or source provenance, stop
  rather than weakening ADR-063/065 guarantees.
- If cross-service dependency registration requires direct database access, stop and
  revise the contract boundary.
- If PostgreSQL physical retirement creates unacceptable lock, vacuum, or bloat
  risk, stop broad cleanup and report the operational evidence.
- If historical dependency status cannot be proven, keep the data protected and do
  not infer release.
- If storage-pressure handling requires weakening strict replay or authorization,
  stop and request an explicit architectural decision.

## Approval Gate

The plan was approved by the human engineer before implementation.
