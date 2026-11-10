# Implementation Plan - Story 0223

## Status

Completed - human approval required before implementation.

This plan implements the accepted ADR-091 domain/preflight workflow only. It
does not execute, expose or authorize physical deletion. Existing unrelated
worktree changes remain outside this plan.

## 1. Persistence Model

### Market Data migration

Add the next additive Market Data Flyway migration after the current highest
version. The migration must not edit existing migrations or mutate the current
evidence relation.

Create provider-neutral tables for:

- retirement operation identity, exact scope, approval reference, request and
  inventory fingerprints, business state, operational state and timestamps;
- append-only retirement transition audit with actor, reason, external
  references and transition fingerprint;
- source-retired dependency acknowledgement keyed by operation identity and
  dependency identity, or an equivalent durable representation that preserves
  the current ordinary dependency records unchanged.

Use explicit check constraints for allowed states, non-empty exact scope fields,
positive depth and immutable operation identity. Add uniqueness constraints for
operation identity and transition identity. Do not add a physical deletion
procedure, trigger, cascade, relation rewrite or automatic scheduler.

### Market Intelligence migration

Add the next additive Market Intelligence Flyway migration only if the existing
durable artifact/dependency persistence cannot represent source-retired state
and acknowledgement safely. Prefer extending the existing artifact dependency
model with an explicit terminal source-retired state and operation/fingerprint
metadata. Do not use the in-memory `ArtifactDependencyRegistry` as the durable
source of truth.

The migration must preserve immutable FeatureSnapshots, outcomes, research
executions, manifests and exports.

## 2. Market Data Domain And Application Layer

Add focused provider-neutral types under the existing Market Data model/service
packages:

- `HistoricalEvidenceRetirementScope` containing provider, provider symbol,
  depth and canonical market identity;
- business and operational state enums matching ADR-091;
- operation request, preflight result, blocker, inventory and readiness views;
- immutable transition/audit view types;
- an application service responsible for validation, idempotent transitions and
  fail-closed reconciliation.

The service must:

1. Validate exact scope and server-derived authorization fields.
2. Create or return the same operation for an identical idempotent request.
3. Reject changed scope, approval, inventory or preflight fingerprint under an
   existing operation identity.
4. Run a read-only preflight that proves every evidence row matches the exact
   scope and fingerprints the result.
5. Inventory artifacts, dependencies, holds, segments, sequences and relevant
   database objects without changing them.
6. Request/validate Market Intelligence dependency acknowledgement.
7. Persist `PREPARED` only after all preparation evidence is durable.
8. Commit `SOURCE_RETIRED` only after the acknowledgement is current and
   consistent with the exact operation/scope/inventory fingerprint.
9. Mark failures and ambiguous outcomes explicitly and require reconciliation
   before retry.
10. Expose physical readiness as a read-only result; never execute PostgreSQL
    cleanup.

The state transition repository must use transaction boundaries and locking
that prevent two operations for the same exact scope from advancing
concurrently. A transition must verify the expected prior state and fingerprint
inside the transaction. Repeated successful transitions must return the
existing durable result rather than append duplicate business effects.

## 3. Exact-Scope Preflight

Extend `OrderBookEvidenceRepository` or add a focused preflight repository for
read-only queries that:

- counts and fingerprints all rows in the target relation;
- rejects any provider, provider symbol or depth outside the requested scope;
- verifies canonical market identity consistency;
- inventories related sequence, segment, artifact, dependency and hold rows;
- checks inbound foreign keys, non-internal triggers and dependent views or
  materialized views through catalog queries;
- returns an explicit unknown/blocker result when a catalog check cannot be
  proven;
- records measured row count and relation size without taking destructive locks.

The fingerprint must be deterministic: stable ordering, normalized null/value
handling and an explicit algorithm/version. The preflight must not rely on an
unbounded in-memory row list.

## 4. Capture Guard

Add a source-retirement read/guard port used by
`OrderBookEvidenceRepository.append` and
`appendSnapshotAsNewSequence`, or by the narrowest shared insert boundary.

Before inserting trusted evidence, the guard must reject a provider/symbol/depth
scope whose durable business state is `SOURCE_RETIRED`. The guard must execute
inside the same transaction as the insert decision so a retirement transition
cannot be bypassed by a concurrent writer. Local observations and unrelated
provider/symbol/depth scopes must remain unaffected.

Failure to read the guard state fails closed for the affected persistence
attempt and must preserve the existing durability-compromised behavior.

## 5. Replay And Artifact Semantics

Extend `OrderBookReplayResult.ReplayStatus` or introduce an explicit compatible
source-retired result representation so replay can return a deterministic
unavailable/source-retired outcome with a safe reason.

Update artifact lookup and reusable-artifact checks so:

- integrity/signature validation remains separate from source reproducibility;
- a source-retired dependency cannot produce replay `AVAILABLE`;
- existing artifact metadata and auditability remain readable;
- promotion cannot create a new source-dependent artifact for a retired scope;
- ordinary `RELEASED` semantics remain unchanged.

Cover both semantic-identity artifact replay and raw evidence replay paths.

## 6. Market Intelligence Acknowledgement And Reconciliation

Extend the existing `OrderBookRetentionClient` with additive internal contract
methods for:

- requesting/recording source-retired dependency acknowledgement;
- reading acknowledgement status for an operation identity;
- reconciling operation identity, exact scope and inventory fingerprint.

Implement the Market Intelligence application service behind the client to:

- identify all affected durable dependencies;
- transition only reviewed dependencies to `SOURCE_RETIRED`;
- preserve consumer identity, reason, timestamp and operation identity;
- reject stale or contradictory fingerprints;
- return explicit partial, timeout and reconciliation-required outcomes;
- keep immutable derived research records unchanged.

Any new API must be internal, authenticated through existing service security,
provider-neutral and bounded. No client-controlled delegated actor or public
physical-operation endpoint may be introduced.

## 7. Controllers And Operational Contract

Add read-only/internal endpoints only if existing controller conventions require
an HTTP boundary for operation preparation, status, preflight or reconciliation.
Use `ResponseEntity`, structured rejection responses and operation identity.

The endpoint contract must not accept a SQL command, table name, cascade flag,
physical operation flag or arbitrary provider wildcard. It must not call
`TRUNCATE`, `DELETE`, `DROP`, or any database mutation path.

Update the operational runbook with:

- external backup restore validation prerequisite;
- exact-scope preflight evidence;
- dependency acknowledgement and reconciliation procedure;
- historical reader/writer quiescence;
- separately authorized `TRUNCATE TABLE ONLY` safeguards;
- post-operation relation/schema/filesystem validation;
- ambiguous-operation recovery and no-blind-retry rules.

## 8. Test Plan

### Market Data unit tests

- exact scope validation and wildcard rejection;
- legal/illegal business and operational transitions;
- idempotent repeated requests;
- changed fingerprint/approval rejection;
- failure versus ambiguous classification;
- source-retired replay and artifact behavior;
- capture guard for retired and unrelated scopes;
- live acquisition preservation through existing capture service fixtures.

### Market Data persistence/integration tests

- additive schema and constraints;
- mixed-scope preflight rejection;
- deterministic inventory fingerprint;
- unknown catalog dependency fail-closed behavior;
- concurrent transition protection;
- operation/audit persistence and reconciliation.

### Market Intelligence tests

- dependency inventory and source-retired transition;
- operation identity/fingerprint matching;
- timeout, partial acknowledgement and contradictory state;
- immutable derived record preservation;
- client contract serialization and internal authorization.

### Safety validation

- structural search proving no new Story code invokes physical deletion;
- isolated migration validation only;
- no runtime database cleanup or provider mutation;
- `git diff --check`.

## 9. Implementation Order

1. Add domain enums/records and repository migration(s).
2. Add Market Data persistence and transition service.
3. Add exact-scope preflight and operation audit.
4. Add Market Intelligence acknowledgement/reconciliation contract.
5. Add capture guard and replay/artifact fail-closed semantics.
6. Add internal read-only controller contract and runbook.
7. Add focused tests, then run relevant module validation.
8. Reconcile documentation and write the Implementation Report.

## 10. Files Expected To Change

Expected surfaces, subject to repository evidence during implementation:

- `market-data/src/main/java/.../model/`
- `market-data/src/main/java/.../service/`
- `market-data/src/main/java/.../controller/InternalOrderBookRetentionController.java`
- `market-data/src/main/resources/db/migration/V26__...sql`
- `market-data/src/test/java/.../service/`
- `market-data/src/test/java/.../repository/`
- `market-data/src/test/java/.../controller/`
- `market-intelligence/src/main/java/.../adapter/marketdata/OrderBookRetentionClient.java`
- `market-intelligence/src/main/java/.../application/`
- `market-intelligence/src/main/java/.../adapter/persistence/`
- `market-intelligence/src/main/resources/db/migration/V26__...sql` when required
- focused Market Intelligence tests
- canonical operational documentation identified during documentation
  reconciliation

No existing migration will be edited in place. Exact filenames and migration
versions must be confirmed immediately before implementation.

## 11. Validation Commands

At minimum after implementation:

```bash
./scripts/test-all.sh
./scripts/quality-verify.sh
git diff --check
```

Targeted Market Data and Market Intelligence module tests should be run first
to shorten feedback time. If the full repository scripts are blocked by
pre-existing unrelated changes or environment constraints, report the exact
blocked check and run the applicable targeted validation instead.

No production restart, deployment, cTrader operation, broker mutation,
physical database cleanup or commit/push is part of this implementation plan.
