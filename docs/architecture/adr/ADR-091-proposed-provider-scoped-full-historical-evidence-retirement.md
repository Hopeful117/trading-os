# ADR-091 - Proposed Provider-Scoped Full Historical Evidence Retirement

| | |
|---|---|
| **Status** | **Accepted - human approval recorded** |
| **Date** | 2026-11-09 |
| **Decision Type** | Exceptional Market Data evidence retirement |
| **Related ADRs** | ADR-057, ADR-060, ADR-062, ADR-067, ADR-069, ADR-079, ADR-080 |

## 1. Context

Trading OS retains canonical order-book evidence for deterministic replay and
historical research. ADR-080 defines ordinary bounded retention at explicit
evidence-segment granularity, requires dependency-aware release and fails closed
for unknown legacy evidence.

The current Market Data database contains a legacy Kraken order-book relation:

```text
database:       market_data
schema:         public
relation:       historical_order_book_evidence
provider:       KRAKEN
provider symbol: PF_XBTUSD
depth:          25
size:           approximately 84.7 GiB including indexes
```

The product owner has approved abandoning historical reproducibility for this
exact scope. Kraken account, portfolio, balances, positions, current prices and
live order-book functionality must remain operational. cTrader historical
research remains the priority.

This product decision does not authorize a database mutation. It establishes a
candidate exceptional retirement scenario requiring explicit technical
governance and separate operational authorization.

## 2. Decision Proposed

Introduce a provider-neutral exceptional full-retirement policy for one exact
provider/symbol/depth scope. Keep ADR-080 unchanged for ordinary retention.

The exceptional policy permits full raw-source retirement only when:

1. A human-approved retirement operation identifies the exact canonical scope.
2. A fresh read-only preflight proves the target relation contains no out-of-
   scope provider, symbol or depth rows.
3. All replay artifacts and protected dependencies in scope are inventoried.
4. A source-retirement domain transition is durably recorded.
5. Source-dependent replay and artifact reuse fail closed after the transition.
6. Historical FeatureSnapshots, outcomes, research executions and exports are
   retained unless a separate domain decision says otherwise.
7. Historical capture for the retired scope is durably blocked while live
   acquisition remains available.
8. An external backup has been restored and validated.
9. The physical operation is separately authorized and audited.

For the current empty-final-state relation, the preferred physical operation is
`TRUNCATE TABLE ONLY` on the exact existing evidence relation. It must not use
`CASCADE`, `RESTART IDENTITY`, `DROP`, or row-by-row deletion.

The workflow is not a distributed transaction between Market Data, Market
Intelligence and PostgreSQL. Each boundary commits its own state, and the next
step is allowed only after preceding durable evidence is visible. Ambiguous
outcomes are reconciled from authoritative state before any retry.

## 3. Domain Semantics

### 3.1 Operation lifecycle

Business state describes what the source means for Trading OS. Operational state
describes what has been proven or performed. They must not be collapsed into one
generic status.

Business state:

```text
PROPOSED -> PREPARED -> SOURCE_RETIRED
                         \-> REJECTED
```

`PREPARED` means the exact scope, dependency inventory, approval reference and
preflight fingerprint are durably recorded, but source retirement has not been
committed. `SOURCE_RETIRED` is terminal for the reviewed scope and means
source-dependent replay is unavailable even if physical cleanup later fails.
`REJECTED` records a definitive fail-closed decision and cannot be reused as
authorization for another operation.

Operational state:

```text
NOT_STARTED -> PREFLIGHT_PASSED -> BACKUP_VALIDATED
                                  -> QUIESCENCE_CONFIRMED
                                  -> PHYSICAL_IN_PROGRESS
                                  -> PHYSICAL_RETIRED

any non-terminal operational state -> FAILED or AMBIGUOUS
FAILED/AMBIGUOUS -> RECONCILIATION_REQUIRED -> retry or terminal rejection
```

The transition rules are:

- `PREFLIGHT_PASSED` requires fresh exact-scope catalog and row-scope
  fingerprints with no unexpected rows or dependent objects.
- `BACKUP_VALIDATED` requires an external restore validation reference; a
  backup merely being present is insufficient.
- `QUIESCENCE_CONFIRMED` requires historical readers and writers for the
  relation to be drained without disabling live acquisition.
- `PHYSICAL_IN_PROGRESS` is entered only by the separately authorized DBA
  operation and carries the operation identity.
- `PHYSICAL_RETIRED` requires post-operation proof that the exact action
  completed and the relation remains structurally valid.
- A timeout, lost connection, unknown commit result or inconsistent read is
  `AMBIGUOUS`, never `FAILED` by assumption. PostgreSQL and the audit record
  must be reconciled before retry.
- A failed preflight, backup validation or quiescence check is terminal for
  that operation identity and may not advance the workflow.

The source-retirement transition is committed before physical deletion. A later
physical failure therefore leaves a known `SOURCE_RETIRED` business state that
must be surfaced and reconciled, not silently rolled back by an application
retry.

### 3.2 Source-retired dependencies

Ordinary `RELEASED` dependencies represent a durable successor result under
ADR-080. Full source retirement has a different meaning and must use an
explicit terminal `SOURCE_RETIRED` state or equivalent immutable marker.

`SOURCE_RETIRED` means:

- raw source reproducibility is intentionally abandoned;
- the historical artifact/dependency record remains auditable;
- the source-dependent artifact is not reusable as an available replay;
- the transition was authorized for an exact scope;
- automatic retention must not infer this state.

### 3.3 Derived research records

FeatureSnapshots, observed outcomes, analysis executions, dataset manifests and
exports remain immutable historical records. This ADR does not authorize their
deletion or mutation. Their provenance may reference a source that is now
retired, and future replay-dependent workflows must expose that limitation.

### 3.4 Live functionality

Historical evidence persistence and live order-book acquisition are separate
capabilities. The retirement scope must block historical evidence inserts for
the retired provider/symbol/depth while preserving live in-memory acquisition,
current prices, account synchronization, balances and positions.

### 3.5 Cross-database consistency

Market Data is authoritative for raw evidence and source-retirement state.
Market Intelligence is authoritative for its own artifact/dependency records.
Because these stores cannot commit atomically:

- Market Intelligence must durably transition reviewed in-scope dependencies
  to `SOURCE_RETIRED` before source retirement is ready.
- Market Data must refuse the transition when acknowledgement or its inventory
  fingerprint is absent, stale or contradictory.
- A timeout or partial acknowledgement creates a reconciliation-required state;
  it does not authorize source retirement or blind retries.
- Reconciliation compares operation identity, exact scope, dependency inventory
  fingerprint and business state in both services. It is idempotent and audited.
- No distributed transaction coordinator, event broker or generic job platform
  is introduced by this decision.

## 4. Invariants

- The scope is exact: provider, provider symbol, depth and canonical market
  identity are all recorded.
- No wildcard provider or symbol scope is permitted.
- No storage-pressure signal can trigger this policy automatically.
- No ordinary ADR-080 dependency release substitutes for source retirement.
- Unknown or unexpected dependencies fail closed.
- Existing artifacts remain auditable and are not silently presented as
  source-reproducible.
- FeatureSnapshots and outcomes remain immutable unless separately approved.
- Live Kraken acquisition and account/portfolio functionality remain separate
  from raw historical persistence.
- Existing Flyway migrations are not modified in place.
- Physical database details remain in the operational adapter/runbook, not
  provider-neutral domain contracts.
- Every physical retirement has immutable operation identity, approval
  reference, preflight fingerprint and post-operation validation.
- Every transition is idempotent, monotonic within its lifecycle and
  attributable to an operation identity.
- Missing exact-scope proof, dependency acknowledgement, backup validation,
  quiescence or post-operation evidence fails closed.

## 5. Physical Retention Decision

The approved exceptional path is:

```text
exact scope preflight
    -> dependency acknowledgement
    -> source-retirement transition
    -> backup/restore validation
    -> replay/capture quiescence
    -> TRUNCATE ONLY evidence relation
    -> post-operation validation
```

No second full relation copy is required by `TRUNCATE`. PostgreSQL preserves the
relation schema and index definitions while releasing physical contents. The
operation requires an `ACCESS EXCLUSIVE` lock and must run only after historical
replay/writer traffic has been drained.

No `CASCADE` is permitted. A fresh catalog preflight must prove no inbound
foreign key exists. No `RESTART IDENTITY` is permitted because evidence uses
UUIDs and unrelated sequence state must not change.

## 6. Audit And Authorization

The implementation must create a dedicated provider-neutral retirement operation
record containing:

- exact scope;
- operation fingerprint;
- approval reference, approver and actor;
- preflight row count and relation size;
- artifact/dependency inventory fingerprint;
- backup/restore validation reference;
- physical operation status;
- post-operation relation/database/filesystem measurements;
- completion or failure reason.

Each transition records actor, timestamp, reason and the relevant fingerprint or
external reference. At minimum the audit distinguishes:

```text
PREPARED
SOURCE_RETIRED
PHYSICAL_RETIRED
FAILED
AMBIGUOUS
RECONCILIATION_REQUIRED
```

The operation identity is unique. Retrying the same logical request reuses it;
changed scope, approval, inventory or preflight fingerprint requires a new
identity. Authorization validates the exact scope, explicit approval reference,
physical action and approval validity. Client claims and storage pressure cannot
establish authority.

## 7. Consequences

### Positive

- Approximately 84.7 GiB of relation storage can be physically reclaimed
  without first copying a second full relation.
- Live Kraken market and account functionality remains available.
- cTrader research is not burdened by obsolete Kraken raw history.
- Ordinary multi-provider retention remains governed by ADR-080.
- Historical derived records and audit metadata remain available.

### Negative

- Raw Kraken order-book replay and independent reproducibility are permanently
  unavailable after commit unless restored from backup.
- Existing artifacts require an explicit source-retired availability state.
- A focused schema/application change is required before operation.
- External backup and restore validation are mandatory.
- The maintenance window must quiesce historical readers/writers.
- Cross-database partial failure requires explicit reconciliation.

## 8. Out Of Scope

- automatic deletion under disk pressure;
- generic provider-independent bulk deletion;
- changing cTrader market-data behavior;
- disabling live Kraken order-book acquisition;
- deleting FeatureSnapshots, outcomes or research datasets;
- changing accepted ADR-080 ordinary eligibility;
- partition migration or generic storage-management infrastructure;
- modifying existing Flyway migrations in place;
- executing any database or runtime operation as part of this proposal.

## 9. Required Implementation And Validation

The implementing Story must provide:

- source-retirement scope and operation persistence;
- explicit dependency transition;
- replay/artifact fail-closed behavior;
- retired-scope capture guard;
- human confirmation and authorization-reference validation;
- exact-scope preflight;
- durable transition audit with idempotent retry/reconciliation;
- cross-database dependency acknowledgement and inconsistency handling;
- regression tests for artifact fallback, replay status and live acquisition;
- operational runbook and backup/restore acceptance criteria.

The physical operation remains a human-controlled DBA action after the Story is
implemented and reviewed.

## 10. Rejection Conditions

Reject the operation if:

- the exact scope cannot be proven;
- an inbound FK or unexpected dependent object exists;
- any dependency/artifact is outside the reviewed inventory;
- backup restore is unverified;
- dependency acknowledgement is missing, stale or contradictory;
- operation state is ambiguous and has not been reconciled;
- source-retired replay still returns `AVAILABLE`;
- historical capture can recreate the retired scope;
- live Kraken or cTrader health is degraded;
- lock/WAL/filesystem reserves are insufficient.

## 11. Acceptance Status

This ADR is accepted for implementation by explicit human confirmation in the
engineering workflow. Acceptance authorizes implementation of the governed
domain and preflight workflow only; it does not authorize a physical database
mutation. The separately authorized DBA operation remains outside this Story.
