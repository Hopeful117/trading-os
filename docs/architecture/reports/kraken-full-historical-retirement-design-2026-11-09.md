# Kraken Full Historical Order-Book Retirement Design

**Date:** 2026-11-09

**Status:** Design for human review; not authorization to execute

**Proposed ADR:** `docs/architecture/adr/ADR-091-proposed-provider-scoped-full-historical-evidence-retirement.md`

**Final verdict:** `DOMAIN_TRANSITION_REQUIRED`

The approved product decision makes complete Kraken `PF_XBTUSD` historical
retirement a valid target. The current PostgreSQL layout makes physical reclaim
technically possible without creating a second 85 GiB relation copy: after the
domain transition, a controlled `TRUNCATE TABLE ONLY public.historical_order_book_evidence`
would remove the heap, indexes and TOAST storage while preserving the table
schema. However, the current application would still be able to treat protected
replay artifacts as reusable after raw evidence removal. That is an unacceptable
provenance failure. A source-retirement transition and external backup/restore
validation must precede the physical operation.

No mutation, retention endpoint, dependency release, artifact invalidation,
configuration change, migration, restart, Docker operation, commit or push was
performed.

## 1. Executive Summary

The product owner approved the principle of abandoning Kraken raw historical
order-book reproducibility while preserving Kraken portfolio, account, current
market-data and live order-book functionality. This design separates that
product decision from operational deletion authorization.

Current relation identity:

```text
container:       market_data-postgres
database:        market_data
schema:          public
relation:        public.historical_order_book_evidence
owner:           postgres
PostgreSQL:      16.15
relation kind:   regular table, not partitioned
```

Current bounded revalidation:

| Item | Current value | Evidence |
|---|---:|---|
| Root filesystem | 449G total, 415G used, 11G available, 98% | `OBSERVED` |
| `market_data` database | 91,276,196,887 bytes | `MEASURED` |
| Evidence relation | 90,962,591,744 bytes | `MEASURED` |
| Evidence heap | 32,848,773,120 bytes | `MEASURED` |
| Evidence indexes | 58,065,190,912 bytes | `MEASURED` |
| Previous exact evidence count | 124,293,051 | `MEASURED` in latest bounded audit |
| Previous provider scope | KRAKEN / PF_XBTUSD / depth 25 | `MEASURED` in latest audit; current exhaustive regroup not repeated |
| Historical capture | `false` | `OBSERVED` |
| Live acquisition | `true`, KRAKEN `PF_XBTUSD` | `OBSERVED` |
| Replay artifacts | 51 | `MEASURED` |
| Dependencies | 51 `PROTECTED` | `MEASURED` |
| Distinct protected sequences | 49 | `MEASURED` |
| Evidence segments | 0 | `MEASURED` |
| Active holds | 0 | `MEASURED` |
| Retirement operations | 1 `DRY_RUN` / `SELECTED` | `MEASURED` |
| Incoming FKs to evidence | 0 | `MEASURED` |
| Non-internal evidence triggers | 0 | `MEASURED` |
| Dependent views/materialized views | 0 / 0 | `MEASURED` |

### Recommended approach

1. Approve the proposed exceptional provider-scoped retirement policy without
   weakening ordinary ADR-080 retention governance.
2. Implement a provider-neutral source-retirement scope and audit transition.
3. Preserve artifact, sequence, dependency, FeatureSnapshot, outcome and
   research records as historical records, but make raw-source-dependent replay
   explicitly unavailable after retirement.
4. Verify an external, restorable backup of the raw relation and domain
   metadata.
5. Quiesce historical replay and raw capture while keeping live acquisition and
   portfolio functionality available.
6. Assert that the relation contains only the approved provider/symbol/depth
   scope.
7. Execute a reviewed `TRUNCATE ONLY` operation without `CASCADE` or
   `RESTART IDENTITY`.
8. Validate relation size, filesystem capacity, schema integrity, live Kraken
   behavior, cTrader OHLC collection and Market Intelligence fail-closed replay
   behavior.

The `TRUNCATE` operation itself does not require a second full relation copy.
The current 11 GB free space is therefore technically sufficient for the
database operation's relation-space effect, but not sufficient for a local
full backup, table rewrite, `pg_repack`, or a rollback copy. External backup
capacity and backup validation remain mandatory.

## 2. Safety Boundary And Evidence Labels

Evidence labels:

- `MEASURED`: read-only SQL or operating-system measurement.
- `CODE_CONFIRMED`: current source code or migration behavior.
- `OBSERVED`: running container configuration or service state.
- `INFERRED`: supported interpretation, not a directly persisted fact.
- `UNVERIFIED`: requires a future approved operation or unavailable evidence.
- `NOT_MEASURED`: deliberately not executed because it was unnecessary or
  unsafe in this preflight.

The current worktree was already dirty. Existing modifications and untracked
reports were not changed or reverted. Only this report and the proposed ADR are
created by this task.

DevLog was unavailable in the current tool environment. Repository source,
accepted ADRs, migrations, running containers and bounded PostgreSQL inspection
were used as authoritative evidence.

## 3. Current Measured Baseline

### Relation storage

The current relation storage is:

| Component | Bytes | Approximate size |
|---|---:|---:|
| Evidence heap | 32,848,773,120 | 30.6 GiB |
| Evidence indexes | 58,065,190,912 | 54.1 GiB |
| Evidence TOAST/other | 48,627,712 | 46.4 MiB |
| **Evidence relation total** | **90,962,591,744** | **84.7 GiB** |

Largest indexes:

| Index | Bytes | Approximate size | Purpose |
|---|---:|---:|---|
| `uk_historical_order_book_fingerprint` | 15,478,751,232 | 14.4 GiB | Unique content fingerprint constraint |
| `ix_historical_order_book_trusted_snapshot` | 14,232,674,304 | 13.3 GiB | Trusted snapshot and temporal selection |
| `ix_historical_order_book_replay` | 14,174,830,592 | 13.2 GiB | Replay market/depth/sequence traversal |
| `ix_historical_order_book_sequence_verification` | 9,137,913,856 | 8.5 GiB | Sequence ordinal and provenance verification |
| `pk_historical_order_book_evidence` | 5,041,020,928 | 4.7 GiB | Stable evidence identity |

The table is not partitioned. All five evidence indexes were valid and ready
at inspection time. Truncation preserves their definitions while resetting
their physical contents.

### Current capture and live behavior

The running Market Data environment reported:

```text
ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false
ORDER_BOOK_ACQUISITION_STARTUP_ENABLED=true
provider=KRAKEN
provider_symbol=PF_XBTUSD
depth=25
```

This is the intended separation:

- historical evidence persistence is disabled;
- live Kraken order-book acquisition remains enabled;
- in-memory live order-book state and current market-data publication remain
  separate from the historical evidence table.

The future retirement scope must add a durable provider-neutral persistence
guard so that accidentally re-enabling capture cannot silently recreate a
retired historical scope. The guard must not disable live acquisition.

### Database metadata

The evidence table has outgoing foreign keys to:

```text
public.markets(market_id)
public.historical_order_book_sequence(sequence_id)
```

No foreign key references the evidence table. There are no non-internal
triggers, views or materialized views directly depending on the table. No
database sequence is owned by the evidence relation. Application code accesses
the table through JDBC repositories, not a JPA entity mapped to the evidence
table.

The relevant Flyway history is:

- `V6__historical_order_book_evidence.sql`: creates sequence, evidence table and
  initial indexes.
- `V11__order_book_retention_lifecycle.sql`: creates replay artifacts,
  dependencies, holds and retirement-operation audit tables.
- `V12__order_book_replay_artifact_provenance.sql`: adds artifact trust and
  source-fingerprint provenance.
- `V14__bounded_order_book_replay_indexes.sql`: adds trusted-snapshot index.
- `V15__order_book_replay_verification_index.sql`: adds sequence-verification
  index.
- `V21__local_order_book_observation_origin.sql`: extends evidence origin.
- `V22__order_book_replay_origin.sql`: adds artifact source origin.
- `V23__order_book_evidence_segments.sql`: adds segment metadata and optional
  dependency/hold segment targets.
- `V25__reconcile_ctrader_market_product_identity.sql`: includes historical
  identity reconciliation for evidence, but does not create a runtime FK from
  another relation to evidence.

The existing migrations must remain unchanged. A future migration is required
only for the proposed source-retirement domain transition, not to remove the
evidence table.

## 4. PostgreSQL Dependency Graph

### Actual relational graph

```text
public.markets
       ▲
       │ evidence.market_id
public.historical_order_book_evidence
       │ evidence.sequence_id
       ▼
public.historical_order_book_sequence
       ▲                 ▲
       │ artifact         │ dependency/hold sequence target
       │                   │
public.historical_order_book_replay_artifact
       ▲
       │ dependency/hold artifact target
public.historical_order_book_evidence_dependency
public.historical_order_book_retention_hold

public.historical_order_book_evidence_segment
       └── sequence_id -> historical_order_book_sequence ON DELETE CASCADE
```

`historical_order_book_evidence_segment` has no FK from an evidence row to a
segment. Membership is represented by sequence ordinal ranges. This is why
zero segment rows means the raw evidence is not classified by the current
retention model.

### Can the relation be truncated without `CASCADE`?

**Database answer: yes, subject to the final preflight assertion.**

The catalog contains no foreign key whose `confrelid` is
`public.historical_order_book_evidence`. The evidence table has only outgoing
foreign keys. PostgreSQL therefore does not require `CASCADE` to truncate this
relation. `CASCADE` must not be used because it would broaden the operation and
would conceal an unexpected future dependency.

The operational procedure must still run a fresh dependency inspection and
fail closed if any inbound FK appears before the maintenance window.

### Does truncation affect other relations?

The PostgreSQL operation affects the physical contents of the evidence heap,
its indexes and its TOAST storage. It does not delete rows from:

- `historical_order_book_sequence`;
- `historical_order_book_replay_artifact`;
- `historical_order_book_evidence_dependency`;
- `historical_order_book_evidence_segment`;
- `historical_order_book_retention_hold`;
- `historical_order_book_retirement_operation`;
- `markets`;
- Market Intelligence tables in another database.

That physical isolation is not sufficient domain safety. The retained artifact
and dependency rows would still describe the deleted source unless the proposed
source-retirement transition is applied first.

### Schema compatibility after truncation

The schema remains present and Flyway validation remains compatible because the
table and indexes are not dropped. SQL statements and JDBC mappings continue to
resolve. Their behavior changes as follows:

- raw replay queries find no trusted snapshot and return unavailable;
- current artifact fallback can incorrectly return an available artifact unless
  the source-retirement transition is implemented;
- evidence fingerprint lookup returns false;
- capture inserts would recreate rows unless the retired-scope guard and
  configuration control are both present;
- sequence and artifact metadata remain, but no longer have raw source rows.

Therefore schema compatibility is **necessary but not sufficient**. The
application domain contract must explicitly represent source retirement.

### Locks and concurrent queries

`TRUNCATE` requires an `ACCESS EXCLUSIVE` lock on the target relation. It
conflicts with ordinary reads and writes. PostgreSQL will wait for existing
conflicting transactions and block new conflicting access while the lock is
held; it does not safely terminate concurrent replay requests for the operator.

The runbook must therefore:

- stop new historical replay and artifact-population traffic;
- drain or cancel approved replay requests before the lock attempt;
- confirm no transaction is reading or writing the relation;
- use a short `lock_timeout` and stop on timeout;
- keep live order-book acquisition enabled only after confirming that its
  historical persistence writer is disabled and cannot insert;
- avoid `CASCADE` and `RESTART IDENTITY`.

If the transaction commits, concurrent replay requests after the transition
must receive an explicit unavailable/source-retired result, never a partial
result or an artifact incorrectly presented as raw-replay available.

### Fingerprints and metadata

The evidence unique fingerprint index is emptied by truncation. Raw content
fingerprints are no longer available for lookup or duplicate prevention. The
serialized artifact and FeatureSnapshot provenance fingerprints remain as
historical identifiers, but they must be interpreted as references to a
retired source, not as proof that the source can still be replayed.

No identity sequence is reset because the procedure must not use `RESTART
IDENTITY`; the table uses UUID evidence IDs and has no owned identity sequence.

## 5. Replay Artifact And Dependency Impact

### Existing artifact contract

`OrderBookReplayArtifact` contains:

- artifact and semantic identity;
- artifact fingerprint;
- market, depth and sequence identity;
- evaluation cutoff and source ordinals;
- provider and provider symbol;
- normalization version;
- aggregate and per-evidence source fingerprints;
- source trust status and origin;
- serialized bid/ask payloads;
- promotion reason and creation time.

The artifact payload is self-contained for displaying a previously materialized
book, but the current contract intentionally protects the source sequence. The
current replay service can return an artifact as `AVAILABLE` when its semantic
identity matches, its dependency is still `PROTECTED`, and its artifact
fingerprint is valid. It does not verify that every source fingerprint still
exists before returning the artifact.

After a raw `TRUNCATE`, leaving the 51 dependencies `PROTECTED` would therefore
allow a false result:

```text
raw evidence absent
source dependency still protected
artifact fingerprint valid
artifact fallback returns AVAILABLE
```

That violates the approved retirement semantics and must not occur.

### Required domain transition

Introduce a provider-neutral terminal source-retirement state, represented by a
new immutable retirement-scope/audit record and an explicit dependency
transition. The recommended semantics are:

```text
PROTECTED
    -> SOURCE_RETIRED
```

`SOURCE_RETIRED` is not ordinary dependency release. It means:

- the owner explicitly authorized abandonment of the raw source;
- the dependent artifact remains as historical audit data;
- the artifact is no longer reusable as an available replay source;
- ordinary retention cannot infer or perform this transition;
- the transition is linked to one exact provider/symbol/depth scope and one
  auditable retirement operation.

The existing `RELEASED` state should remain reserved for the current
successor-result contract. Reusing it for full source retirement would blur two
different meanings and could make ordinary retention treat an exceptional
retirement as a normal lifecycle release.

The proposed migration must update the dependency state constraint and domain
enum, add the source-retirement scope/operation record, and update replay and
artifact queries to fail closed for `SOURCE_RETIRED`.

### Record classification

| Record | Required disposition | Reason |
|---|---|---|
| Raw evidence rows | Physically retire after transition | Product owner approved abandoning raw reproducibility |
| Evidence heap/indexes/TOAST | Reclaim through `TRUNCATE ONLY` | Empty final dataset; no second relation copy required |
| Sequence rows | Retain as historical metadata, marked source-retired by scope | Needed for artifact/audit identity; small storage footprint |
| Replay artifacts | Retain as historical payloads, not reusable replay sources | Do not silently delete research history; source is gone |
| Protected dependencies | Transition to explicit `SOURCE_RETIRED` | Prevent false artifact reuse while retaining audit history |
| Evidence segments | Keep empty in current state | No segment backfill is needed for full-scope truncation after exception approval |
| Retention holds | Retain if any appear in future preflight; current count is zero | Holds are audit records, not deletion shortcuts |
| Retirement operation | Add immutable full-retirement operation record | Proves exact scope, approval and physical action |
| FeatureSnapshots | Retain unchanged | Immutable projections remain historical facts |
| ObservedMarketOutcomes | Retain unchanged | Objective outcomes are independent immutable facts |
| Analysis executions/research records | Retain unchanged | Do not delete or invalidate research history automatically |
| Dataset manifests/exports | Retain unchanged, with source-retirement disclosure | Existing exports remain historical outputs; future replay-dependent exports fail closed |
| Market catalogue | Retain unchanged | Kraken market remains supported for live/account workflows |
| Live order-book state | Retain and continue | It is not owned by the historical evidence relation |

### FeatureSnapshots and outcomes

ADR-060 makes FeatureSnapshots immutable projections with provenance. ADR-062
makes research datasets immutable manifests/exports. ADR-057 makes source
provenance part of historical evidence semantics. The approved retirement does
not authorize deleting or mutating those facts.

The design therefore does not invalidate existing snapshots or outcomes. It
adds source availability semantics at the Market Data/replay boundary:

- historical snapshots and outcomes remain readable as facts;
- the source provenance remains recorded;
- a new request that requires raw Kraken order-book replay returns
  `SOURCE_RETIRED` or `UNAVAILABLE`, not `AVAILABLE`;
- future dataset validation that requires replay evidence records the explicit
  exclusion/failure reason;
- existing exported data remains an historical export and is not silently
  relabeled as reproducible.

If product governance later requires all derived records to be invalidated,
that is a separate domain decision and must not be folded into this physical
storage operation.

## 6. ADR-080 Compatibility And Evolution

ADR-080 remains correct for ordinary retention. It deliberately fails closed on
unknown legacy evidence and requires explicit segments, dependencies, holds and
trusted boundaries. Full provider-scoped retirement is a different exceptional
scenario: the product owner explicitly accepts loss of raw reproducibility and
authorizes a complete exact-scope retirement after review.

### Recommended ADR treatment

Adopt a **dedicated exceptional retirement ADR referencing ADR-080** rather than
amending ADR-080's ordinary eligibility rules or replacing it.

The proposed ADR must state:

- it is exceptional and provider/symbol/depth scoped;
- it requires explicit human architecture and product approval;
- it requires a fresh exact-scope preflight;
- it requires artifact/dependency transition before physical deletion;
- it preserves immutable research records but marks source replay unavailable;
- it does not release dependencies through ordinary successor-result semantics;
- it does not bypass active holds or untrusted-artifact review silently;
- it is never triggered automatically by storage pressure;
- it does not authorize future generic bulk deletion;
- ordinary multi-provider retention remains governed by ADR-080.

The proposed ADR is intentionally not accepted. It is drafted separately at
`docs/architecture/adr/ADR-091-proposed-provider-scoped-full-historical-evidence-retirement.md`.

## 7. Candidate Physical Retirement Strategies

| Strategy | Final physical result | Second 85 GiB copy | Locking | WAL/temporary requirements | Domain safety | Verdict |
|---|---|---|---|---|---|---|
| Existing provider-scoped retention service | Deletes only eligible bounded rows | No | Bounded sequence locks | Row-level deletion/WAL; physical space not returned predictably | Current zero segments and protected deps make it ineligible | Not suitable for complete physical reclaim |
| `DELETE` all evidence rows | Empty heap logically | No second relation copy, but large row-delete/WAL burden | Long-running row locks and bloat | Potentially very large WAL; exact amount not measured | Bypasses intended segment semantics; not approved | Reject |
| `TRUNCATE ONLY` evidence relation | Empty heap, indexes and TOAST physically | No | `ACCESS EXCLUSIVE` on evidence relation | WAL behavior and backup size must be validated; no rewrite copy | Safe only after domain transition and exact scope assertion | Recommended |
| Drop/recreate relation through Flyway | Empty/recreated schema | May avoid data copy, but DDL migration/cutover required | `ACCESS EXCLUSIVE`, schema outage | DDL WAL and migration recovery; no current migration | Breaks schema history/one-time ops boundary; unnecessary | Reject for this operation |
| Table rewrite/repack | Empty/reduced replacement relation | Yes, generally shadow storage | Long copy plus final lock | Requires relation/index/WAL/rollback headroom | More complex than empty final state | Reject under current capacity |
| Partition migration then drop | Physical range drop | Requires migration copy | Cutover locks and migration | Requires new partitioned design and capacity | Future architecture, not current schema | Defer |

### 7.1 Existing retention service

The existing service is not a complete-retirement mechanism. It requires segment
assessment, released dependencies and eligibility. It records a dry-run row
even for preview, deletes only bounded candidates, and leaves the relation
structure intact. It is correct for ordinary ADR-080 retention but cannot
efficiently reclaim the whole provider relation under the approved exceptional
decision.

### 7.2 `DELETE` all rows

This would preserve the table schema but is inferior to `TRUNCATE` for an empty
final dataset. It would process 124M rows, generate substantial row-delete WAL,
retain dead tuples until vacuum, hold row-level locks over a long operation and
not immediately return the 84.7 GiB to the filesystem. It also makes rollback
and failure behavior more complicated. It must not be used as a substitute for
the reviewed truncate operation.

### 7.3 `TRUNCATE ONLY`

This is the only candidate that directly matches the final state and current
physical layout:

- no retained-row copy is required;
- heap, index and TOAST files are truncated in place;
- table and index definitions remain;
- outgoing FKs remain;
- no `CASCADE` is required because no inbound FKs exist;
- `RESTART IDENTITY` is unnecessary and must not be used;
- the operation is transactional before commit, but committed data removal is
  not recoverable without backup/restore;
- physical relation space should fall close to relation metadata/index minimums
  after commit, subject to PostgreSQL version/filesystem behavior.

The exact WAL volume, lock wait and wall-clock duration are `UNVERIFIED` and
must be measured or bounded during the approved maintenance window. No exact
duration is invented here.

### 7.4 Drop/recreation through Flyway

Flyway migrations are schema history, not an appropriate one-time operational
data command. Dropping the relation would make the running application fail
until a migration recreated it, and a new migration that drops/recreates the
same table would complicate repeatability, rollback and clean-environment
behavior. The final approved dataset is empty but the provider-neutral table
contract remains needed for future approved providers and live architecture.

Preserve the relation and use `TRUNCATE ONLY` instead.

## 8. Recommended Domain And Physical Design

### 8.1 New provider-neutral retirement scope

Add an immutable Market Data retirement scope record with at least:

```text
retirement_id
provider
provider_symbol
market_id or canonical market identity assertion
depth
scope_fingerprint
authorization_reference
approved_by
approved_at
state: PREPARED | SOURCE_RETIRED | PHYSICAL_RETIRED | FAILED
evidence_row_count_before
relation_size_before
operation_id
created_at
completed_at
```

The scope must be provider-neutral in schema and code. Kraken is only the first
approved scope value. It must not become a generic wildcard or an environment
variable that can silently authorize arbitrary deletion.

### 8.2 Artifact/dependency transition

Add an explicit `SOURCE_RETIRED` terminal dependency state or equivalent
retirement marker. The transition must require:

- exact scope match;
- all affected artifacts enumerated;
- no hidden dependency outside the inspected relation model;
- explicit authorization reference;
- immutable audit record;
- current source state `PROTECTED`;
- a human-confirmed decision that raw reproducibility is abandoned.

The transition must happen before `TRUNCATE`. Replay artifact queries must
exclude source-retired dependencies and return a typed source-retired result.

### 8.3 Capture guard

The historical evidence writer must check the retirement scope before inserting
evidence. If a scope is `SOURCE_RETIRED` or `PHYSICAL_RETIRED`, persistence must
be rejected with an explicit operational reason while live acquisition and
in-memory publication continue. This is a provider-neutral guard and does not
disable current Kraken prices, balances, positions or live order-book behavior.

### 8.4 Physical operation

After the transition and final exact-scope assertion, the reviewed SQL is:

```sql
-- NOT AUTHORIZED — DO NOT EXECUTE in this design task.
-- Future reviewed maintenance operation only.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
TRUNCATE TABLE ONLY public.historical_order_book_evidence;
COMMIT;
```

The final procedure must not use `CASCADE`, `RESTART IDENTITY`, `DROP`,
`DELETE`, `VACUUM FULL`, `REINDEX` or `CLUSTER`.

This SQL is safe only after the domain transition, exact scope assertion,
external backup verification and service quiescence. The `30s` values are
reviewable starting points, not a guaranteed execution duration; the DBA may
adjust them in the approved operation plan after measuring the environment.

## 9. Required Disk, WAL And Capacity Headroom

### Direct truncate

The current approximately 11 GB free is **not a blocker for the physical
relation operation itself**, because `TRUNCATE` does not create a second full
relation copy. It is still an unsafe operating margin for a production window.

Required conditions:

- external backup storage, not the nearly full root filesystem;
- an agreed post-operation free-space reserve;
- sufficient WAL capacity and archive/replication handling for the database
  configuration;
- no competing high-volume writes;
- lock timeout and abort procedure;
- enough free space for PostgreSQL normal operation during the window.

The exact WAL requirement is `UNVERIFIED`. It must not be guessed. PostgreSQL
`TRUNCATE` WAL behavior is materially different from 124M row deletes, but WAL
and archive capacity still need DBA verification against the current
`wal_level`, archiving, replication and backup configuration.

### Operations that do require additional capacity

The current free space is insufficient for:

- local full backup of the raw relation;
- `pg_repack` or a shadow-table rewrite;
- controlled table reconstruction;
- partition migration through a copied relation;
- retaining a local rollback copy after physical deletion.

External backup storage or an approved additional PostgreSQL volume remains
mandatory even though the final truncate operation itself does not copy the
relation.

## 10. Backup And Recovery Requirements

### Current state

No verified backup or restore procedure was found in the repository. No backup
artifact, `pg_dump`, `pg_restore`, `pg_basebackup` or volume snapshot was
created or tested during this task. Current backup readiness is therefore
`UNVERIFIED`.

### Required backup decision

Before physical retirement, the DBA must choose and validate one supported
external recovery mechanism:

- a consistent PostgreSQL physical backup/snapshot that includes the evidence
  relation and domain metadata;
- a logical backup sufficient to restore the raw relation, dependent retention
  metadata and application schema;
- an approved storage-level snapshot with documented PostgreSQL consistency and
  restore procedure.

The backup must be stored outside the nearly full root filesystem and retained
until post-retirement acceptance is complete. A backup of only the 51 artifact
rows is not sufficient to restore raw evidence and is not a rollback for
truncation.

### Restore validation

The owner must restore the backup into an isolated PostgreSQL instance and
verify:

- the exact evidence row count or a documented equivalent;
- relation/index integrity;
- the 51 artifacts and 51 protected dependencies;
- replay behavior before retirement;
- FeatureSnapshot and outcome records where included;
- application schema compatibility.

Without this restore test, the final verdict remains blocked even after the
domain transition is implemented.

### Recovery after commit

`TRUNCATE` is transactional until commit. A failure before commit can roll back
the database transaction. After commit, removed raw evidence cannot be
recovered from PostgreSQL transaction rollback. Recovery requires restoring the
verified external backup and reconciling any approved post-backup changes.

The source-retirement transition is also not treated as automatically
reversible. Reversing it would require a separate approved restoration workflow
and evidence that the raw source was restored consistently.

## 11. Operational Runbook

This is a future reviewed runbook. It is not execution authorization.

### Phase A — Preflight

#### A1. Confirm exact scope

Read-only checks must confirm:

- database `market_data` and schema `public`;
- target relation identity and owner;
- relation is not partitioned unexpectedly;
- exact approved provider/symbol/depth scope;
- no inbound FK, trigger, view, materialized view or unexpected dependency;
- no additional provider rows exist in the target relation.

The final provider assertion must be exhaustive and read-only. If it times out,
returns an unexpected provider/symbol/depth, or cannot prove scope, stop.

Example future assertion:

```sql
-- Read-only preflight; execute only in an approved maintenance window.
SET TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '60s';
SELECT EXISTS (
    SELECT 1
      FROM public.historical_order_book_evidence
     WHERE provider <> 'KRAKEN'
        OR provider_symbol <> 'PF_XBTUSD'
        OR depth <> 25
) AS contains_out_of_scope_rows;
```

The expected result is `false`. A `false` result is necessary but not sufficient;
the scope authorization must also match the canonical market identity.

#### A2. Confirm runtime safety

- verify historical capture remains disabled;
- verify live Kraken acquisition is enabled and healthy;
- verify Kraken balances, positions and current prices;
- verify cTrader OHLC collection and current Market Intelligence health;
- disable or quiesce historical replay, artifact population and retention
  schedulers through an approved operational mechanism;
- drain active replay requests;
- confirm no capture writer transaction is active;
- confirm no concurrent database maintenance operation exists.

Do not disable live acquisition merely to disable historical persistence.

#### A3. Confirm backup and capacity

- verify external backup exists;
- restore it in isolation;
- verify the restore checks in Section 10;
- verify WAL/archive headroom;
- verify the post-operation filesystem reserve;
- record pre-operation relation sizes and filesystem capacity.

Stop if backup readiness, restore validation, WAL capacity or lock ownership is
unknown.

### Phase B — Domain retirement preparation

#### B1. Create the retirement operation identity

Create the provider-neutral retirement operation through the future approved
service/command. The operation must record:

- exact provider, symbol, depth and canonical market identity;
- preflight relation size and row count;
- dependency/artifact count and IDs;
- authorization reference and approver;
- operation fingerprint;
- planned physical operation;
- explicit statement that raw reproducibility is abandoned.

This is a future application operation and must not be emulated with ad hoc SQL.

#### B2. Transition source-dependent records

Within the approved domain transaction:

1. Freeze the exact scope.
2. Enumerate all 51 protected dependencies and their 51 artifacts.
3. Verify no dependency outside the enumerated scope references the target.
4. Record the source-retirement decision.
5. Transition affected dependencies to `SOURCE_RETIRED`.
6. Mark the retirement scope `SOURCE_RETIRED`.
7. Make artifact/replay queries reject source-retired artifacts.
8. Keep artifacts, sequences, snapshots, outcomes and research records intact.

Failure in any step stops the operation before database truncation.

#### B3. Verify fail-closed behavior before truncation

In a test or approved staging environment, verify:

- raw replay with the retired scope returns `SOURCE_RETIRED` or
  `UNAVAILABLE`;
- semantic artifact replay does not return `AVAILABLE`;
- capture persistence rejects the retired scope;
- live order-book acquisition still publishes current state;
- FeatureSnapshots/outcomes remain readable;
- future replay-dependent research reports an explicit exclusion.

Do not proceed if an artifact remains reusable after its dependency transition.

### Phase C — Physical retirement

#### C1. Quiesce the relation

Keep live acquisition running only if its persistence path is proven disabled.
Block new historical replay and drain existing transactions. Confirm lock
ownership with read-only `pg_stat_activity`/`pg_locks` inspection.

Stop conditions:

- any active evidence writer;
- any unexpected replay transaction;
- any lock wait beyond the approved threshold;
- any out-of-scope evidence row;
- source-retirement transition incomplete.

#### C2. Execute the reviewed operation

```sql
-- NOT AUTHORIZED — DO NOT EXECUTE in this task.
-- Future DBA execution only after all approvals and preconditions pass.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
TRUNCATE TABLE ONLY public.historical_order_book_evidence;
COMMIT;
```

The DBA must use the exact approved relation identity. Do not add `CASCADE` or
`RESTART IDENTITY`. If the lock or statement timeout is reached, abort and
investigate; do not increase the timeout blindly.

#### C3. Record completion

After commit, update the dedicated retirement operation record with:

- commit timestamp;
- actual pre/post relation sizes;
- actual pre/post database size;
- actual filesystem free space;
- operation fingerprint and approver;
- validation result;
- explicit source-retirement state.

The audit update must be performed through the approved service or reviewed
operation path, not an ad hoc unrelated database edit.

### Phase D — Post-retirement validation

#### D1. PostgreSQL

- evidence table row count is zero;
- evidence heap/index/TOAST sizes have fallen to relation minimums;
- table, indexes, outgoing FKs and constraints remain present;
- no inbound FK unexpectedly appeared;
- no invalid indexes exist;
- sequence/artifact/dependency/hold/operation rows match the approved policy;
- no long-running transaction or lock remains;
- database accepts connections;
- filesystem free space increased by the measured amount.

#### D2. Market Data

- live Kraken order-book acquisition remains healthy;
- current Kraken prices remain fresh;
- historical evidence capture remains disabled or source-retired guarded;
- no unexpected evidence insertion occurs;
- cTrader OHLC capture remains healthy;
- Market Data health reports no durability/storage false positive.

#### D3. Trading Core and Broker functionality

- Kraken account synchronization remains healthy;
- balances and positions remain visible;
- no broker credentials, provider connection or account authorization changed;
- no gateway or service-to-service authentication regression appears.

#### D4. Market Intelligence

- existing FeatureSnapshots and outcomes remain readable;
- research records and exports remain readable;
- raw Kraken order-book replay returns explicit source-retired/unavailable;
- no protected artifact returns `AVAILABLE` through semantic fallback;
- cTrader historical research remains unaffected;
- logs contain no false-success replay or silent partial result.

### Phase E — Prevention

- keep `ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false` unless a new approved
  research objective enables it;
- persist provider-neutral retirement scope so configuration cannot silently
  recreate retired historical evidence;
- preserve live order-book acquisition independently;
- alert on any attempted insert into a source-retired scope;
- monitor relation size, WAL, filesystem headroom and capture state;
- keep ordinary retention governed by ADR-080;
- require a new explicit product and architecture decision to restore raw
  historical capture for Kraken.

## 12. Failure And Stop Conditions

Stop immediately before physical commit if any condition occurs:

- backup restore has not been validated;
- exact scope assertion cannot complete or returns out-of-scope rows;
- dependency/artifact inventory differs from the approved 51/51 baseline;
- source-retirement transition is incomplete or partially applied;
- artifact replay still returns `AVAILABLE` after transition;
- capture writer is active or can insert the retired scope;
- an inbound FK, trigger, view or materialized view is discovered;
- lock acquisition exceeds the approved timeout;
- WAL/archive or filesystem reserve is insufficient;
- live Kraken or cTrader health is degraded before the operation;
- any relation other than the approved evidence relation would be affected;
- post-commit validation cannot prove the evidence relation is empty and the
  schema remains intact.

No automatic retry is allowed after an ambiguous database or domain-operation
result. Reconcile operation state and PostgreSQL state first.

## 13. Engineering Story Proposal

No Story was created. The minimum implementation should be one focused Story,
with an optional operations subtask:

### Story: Provider-Scoped Full Historical Evidence Retirement

**Goal:** support one explicitly authorized provider/symbol/depth full-retirement
scenario without weakening ordinary multi-provider retention.

**Scope:**

- provider-neutral immutable retirement scope and operation audit;
- explicit `SOURCE_RETIRED` dependency state or equivalent marker;
- fail-closed replay/artifact behavior;
- historical capture guard that preserves live acquisition;
- exact-scope preflight;
- human confirmation and authorization reference;
- controlled database-operation runbook integration;
- regression tests for replay fallback, capture guard and artifact retention;
- operational metrics and post-retirement validation.

**Out of scope:**

- generic storage-management framework;
- automatic storage-pressure deletion;
- changing ADR-080 ordinary segment retention;
- deleting FeatureSnapshots, outcomes, research executions or exports;
- disabling live Kraken order-book acquisition;
- changing cTrader market-data architecture;
- implementing a general partition migration.

**Acceptance criteria:**

- exact scope cannot include another provider, symbol or depth;
- no operation is executable without explicit human confirmation;
- current 51 artifacts remain auditable;
- source-retired dependencies cannot satisfy reusable-artifact queries;
- raw replay returns typed source-retired/unavailable;
- historical capture rejects retired scope while live order-book state remains
  operational;
- an approved future DBA runbook can use `TRUNCATE ONLY` without `CASCADE`;
- tests prove no FeatureSnapshot/outcome deletion occurs;
- backup/restore and post-operation validation requirements are documented;
- no existing Flyway migration is altered.

### Optional operational subtask

Validate the external backup, restore path, WAL/archive capacity and a
maintenance-window execution in an isolated or staging environment. This is an
operations prerequisite, not application feature work.

## 14. Required Human Approvals

Before implementation:

- acceptance of the proposed exceptional ADR;
- approval of `SOURCE_RETIRED` semantics for protected dependencies;
- approval to preserve artifacts, sequences, FeatureSnapshots, outcomes and
  research records as historical but non-replayable where applicable;
- approval of the capture guard and live-functionality boundary;
- approval of the Engineering Story.

Before execution:

- exact provider/symbol/depth and canonical market identity;
- all 51 artifact/dependency inventory and impact review;
- external backup and isolated restore validation;
- WAL/archive and post-operation filesystem reserve;
- maintenance window and lock timeout;
- exact `TRUNCATE ONLY` operation authorization;
- pre/post validation owner and stop authority;
- rollback/recovery owner.

## 15. Remaining Blockers

### Dominant blocker: domain transition

The current application has no explicit source-retired state. Without a code and
schema transition, protected artifact fallback can present deleted raw evidence
as available. This is a confirmed design defect for full retirement.

### Backup blocker

No verified external backup or restore procedure was found. A committed
truncate is not reversible without one.

### Scope verification blocker

The latest audit measured all current rows as Kraken `PF_XBTUSD` depth 25, and
the current runtime capture configuration matches. A new exhaustive provider
assertion was not repeated in this preflight because the user requested bounded
read-only diagnostics. The future execution window must perform that assertion
and stop on timeout or mismatch.

### Operational blocker

The host remains at 98% filesystem usage. Direct truncate does not require a
second relation copy, but external backup storage, WAL/archive headroom and a
safe post-operation reserve remain necessary.

## 16. Final Verdict

### `DOMAIN_TRANSITION_REQUIRED`

The complete-retirement physical mechanism is technically concrete: after
authorization and domain transition, PostgreSQL can truncate the exact existing
relation without `CASCADE` and without a second 85 GiB relation copy. The
current schema has no inbound FKs, triggers, views or materialized views that
would make the operation structurally impossible.

The operation is not currently ready for execution because:

- protected replay artifacts would otherwise remain falsely reusable;
- dependency semantics do not distinguish ordinary release from source
  retirement;
- the historical capture writer needs a durable retired-scope guard;
- backup and restore readiness is unverified;
- final exact provider-scope assertion remains pending.

After the proposed domain transition is implemented and backup/scope validation
passes, the operation can be reviewed as `FULL_RETIREMENT_DESIGN_READY`. Product
approval of the retirement principle does not itself authorize any database
mutation.
