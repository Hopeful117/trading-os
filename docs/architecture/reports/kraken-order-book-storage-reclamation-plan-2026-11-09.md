# Kraken Order-Book Storage Reclamation Plan

**Date:** 2026-11-09

**Scope:** read-only storage, PostgreSQL, retention and production-operations
preflight. No cleanup, runtime change, database mutation, container restart,
Docker cleanup, commit or push was performed.

**Final verdict:** `ADDITIONAL_STORAGE_REQUIRED`

Additional capacity is the dominant blocker for physical reclamation. A second
blocker remains: the legacy evidence has no segment metadata, so the existing
dependency-aware retention implementation cannot identify any evidence as
eligible for retirement without an unexecuted backfill and later classification
decision. The current 11 GB of filesystem headroom is not sufficient for a
safe table rewrite or online repack of an approximately 85 GiB relation.

## 1. Executive Summary

The current PostgreSQL instance was revalidated against the previous audit.
The actual database and relation identity are:

```text
container:       market_data-postgres
database:        market_data
schema:          public
relation:        public.historical_order_book_evidence
owner:           postgres
PostgreSQL:      16.15
relation kind:   regular table, not partitioned
```

Current measured baseline:

| Item | Current value | Evidence |
|---|---:|---|
| Root filesystem | 449G total, 415G used, 11G available, 98% | `OBSERVED` |
| PostgreSQL database `market_data` | 91,276,188,695 bytes, approximately 85 GiB | `MEASURED` |
| Evidence rows | 124,293,051 | `MEASURED`, exact `COUNT(*)` completed |
| Evidence relation total | 90,962,591,744 bytes, approximately 84.7 GiB | `MEASURED` |
| Evidence heap | 32,848,773,120 bytes, approximately 30.6 GiB | `MEASURED` |
| Evidence indexes | 58,065,190,912 bytes, approximately 54.1 GiB | `MEASURED` |
| Evidence TOAST/other relation storage | 48,627,712 bytes | `MEASURED` |
| Evidence row estimate | 120,427,504 | `MEASURED`, stale catalog estimate only |
| Evidence capture | Disabled | `OBSERVED` from runtime configuration |
| Live order-book acquisition | Enabled for Kraken BTC/USD | `OBSERVED` |
| Sequences | 1,869 exact rows: 1,759 `SEALED`, 110 `INVALIDATED` | `MEASURED` |
| Replay artifacts | 51 | `MEASURED` |
| Dependencies | 51, all `PROTECTED` | `MEASURED` |
| Distinct protected sequences | 49 | `MEASURED` |
| Evidence segments | 0 | `MEASURED` |
| Retention holds | 0 | `MEASURED` |
| Retirement operations | 1 `DRY_RUN` / `SELECTED` | `MEASURED` |

The previous audit measured every evidence row as the following provider scope,
and the current capture configuration still targets the same scope:

```text
provider:        KRAKEN
provider symbol: PF_XBTUSD
product type:    LINEAR_PERPETUAL
depth:           25
```

The current exact row count was revalidated. A fresh exhaustive provider
regroup was not repeated because the broader grouping query exceeded the
bounded inspection timeout under current disk pressure. Therefore the current
provider attribution is `MEASURED` from the prior audit plus current runtime
configuration, while a same-second exhaustive regroup is `NOT_MEASURED`.

The relation is not partitioned. Its largest indexes are the unique content
fingerprint index at 14.4 GiB, the trusted-snapshot index at 13.3 GiB, the
replay index at 13.2 GiB, the sequence-verification index at 8.5 GiB and the
primary key at 4.7 GiB.

The safest practical path is staged, not immediate deletion:

1. Add temporary or permanent PostgreSQL storage before any physical rewrite.
2. Verify backup and restore capability, service health and live capture state.
3. Treat the existing legacy dataset as `UNCLASSIFIED` until segment metadata
   and provenance are independently classified.
4. Use the existing retention model only after a human-approved classification
   and dependency review.
5. Retire only governed eligible ranges, then use a separately approved
   physical reclamation method on a relation design that can reclaim filesystem
   space safely.

The current application supports logical segment retirement, but it does not
provide an immediate safe physical reclaim for this legacy non-partitioned
table. Existing logical deletion would create reusable PostgreSQL space, not
predictably return filesystem capacity to the host.

## 2. Safety Boundary And Evidence Labels

No prohibited operation was executed. Specifically, this audit did not execute
`DELETE`, `TRUNCATE`, `DROP`, `ALTER TABLE`, `VACUUM FULL`, `REINDEX`,
`CLUSTER`, partition operations, table reconstruction, segment backfill,
dependency release, retention execution, Docker prune, volume deletion,
container restart, configuration change or broker operation.

The existing worktree was already dirty. This report is the only file created by
this task. Existing modifications and untracked reports were not reverted.

Evidence labels:

- `MEASURED`: read-only PostgreSQL or operating-system measurement.
- `CODE_CONFIRMED`: established from current source code or migrations.
- `OBSERVED`: established from running container configuration or status.
- `INFERRED`: supported interpretation, not a direct persisted fact.
- `UNVERIFIED`: requires an approved future operation or unavailable evidence.
- `NOT_MEASURED`: deliberately not run because it could be unsafe, expensive or
  outside this non-destructive scope.

DevLog was unavailable in the current tool environment. The repository, ADRs,
current containers, Docker metadata, PostgreSQL catalogs and bounded SQL were
used as the authoritative sources for this plan.

## 3. Verified Storage And Capacity Baseline

### Filesystem

```text
filesystem: /dev/mapper/data-root
type:       ext4
total:      449G
used:       415G
available:  11G
usage:      98%
```

The available value is rounded by `df`; the exact free byte count was not
needed for the decision. This is insufficient headroom for an approximately 85
GiB table rewrite, a second full copy, a `VACUUM FULL`, or an online repack
requiring a shadow table and rebuilt indexes.

### Docker

`docker system df` reported:

| Docker category | Total | Reclaimable | Interpretation |
|---|---:|---:|---|
| Images | 17.25 GB | 5.373 GB | Not used in this plan; Docker prune is forbidden |
| Containers | 151 MB | 0 B | Active containers; no cleanup performed |
| Local volumes | 97.48 GB | 442.2 MB | PostgreSQL data is volume-backed; no volume cleanup performed |
| Build cache | 13.74 GB | 2.059 GB | Not used in this plan; Docker prune is forbidden |

The PostgreSQL data mount is:

```text
/var/lib/docker/volumes/trading-os_market_data/_data
  -> /var/lib/postgresql/data
read-write: true
```

This confirms the data is stored in the Docker volume
`trading-os_market_data`. No volume deletion, pruning or filesystem inspection
that would modify it was performed.

The Docker reclaimable image/cache values are not counted as approved database
reclamation. They are separate operational decisions and would not solve the
retention-governance blocker.

### PostgreSQL databases

The active PostgreSQL container exposes `market_data`, `postgres` and template
databases. The requested `trading_os` database does not exist in this container;
the correct database is `market_data`.

| Database | Current size |
|---|---:|
| `market_data` | 91,276,188,695 bytes, approximately 85 GiB |
| `postgres` | approximately 7.4 KiB |
| `template1` | approximately 7.3 KiB |

### Evidence relation

The relation is `public.historical_order_book_evidence`, not
`market-data.historical_order_book_evidence`. PostgreSQL metadata confirms:

```text
relkind:       r (regular table)
owner:         postgres
partitioned:   false
heap:          32,848,773,120 bytes
indexes:       58,065,190,912 bytes
total:         90,962,591,744 bytes
toast/other:   48,627,712 bytes
```

The table has no partition children. It has foreign keys to `markets` and
`historical_order_book_sequence`, a primary key on `evidence_id`, and a unique
constraint on `content_fingerprint`.

### Current bloat/reclaimability signal

PostgreSQL statistics reported `n_live_tup=0`, `n_dead_tup=0`, no recorded
vacuum/autovacuum and no recorded analyze/autoanalyze for the evidence table.
This is stale or incomplete statistics, because an exact count found 124M rows.
It cannot be used to estimate bloat or reclaimable dead tuples.

The `pgstattuple` extension is available but not installed. It was not enabled
or invoked because installing or creating an extension is a database change and
because a full physical scan would be inappropriate under 11 GB free space.

No substantial reclaimable dead-tuple capacity is established. The current
safe estimate is **unknown**, not zero and not positive.

## 4. Relation And Index Breakdown

### Required storage table

| Component | Current size | Potential reclamation now | Confidence |
|---|---:|---:|---|
| Evidence heap | 32,848,773,120 B / 30.6 GiB | Unknown without approved logical retirement; 0 GiB safely approved now | High for size, low for reclaimability |
| Unique fingerprint index | 15,478,751,232 B / 14.4 GiB | Follows heap contents; physical reclaim requires index rebuild or relation replacement | High |
| Trusted snapshot index | 14,232,674,304 B / 13.3 GiB | Follows heap contents; no standalone safe reclaim identified | High |
| Replay index | 14,174,830,592 B / 13.2 GiB | Follows heap contents; required by replay queries | High |
| Sequence verification index | 9,137,913,856 B / 8.5 GiB | Follows heap contents; required by provenance verification | High |
| Primary key index | 5,041,020,928 B / 4.7 GiB | Follows heap contents; required for identity and foreign-key behavior | High |
| Evidence TOAST/other | 48,627,712 B / 46.4 MiB | Small and not independently actionable | High |
| Related retention tables | Approximately 0.5 MiB total, excluding artifact TOAST | Negligible | High |
| Other `market_data` database storage | Approximately 313 MiB outside the evidence relation | Not in scope for this cleanup | Medium |

The evidence indexes are approximately 63.8% of the evidence relation total.
Their size is not evidence of corruption. Each has an architectural purpose:

- `uk_historical_order_book_fingerprint` enforces immutable content identity
  and prevents duplicate evidence fingerprints.
- `ix_historical_order_book_trusted_snapshot` supports trusted snapshot and
  temporal replay selection.
- `ix_historical_order_book_replay` supports market/depth/sequence replay
  selection ordered by known time and ordinal.
- `ix_historical_order_book_sequence_verification` supports contiguous sequence
  and provenance verification.
- `pk_historical_order_book_evidence` preserves stable evidence identity and
  supports foreign-key/reference semantics.

The index validity flags are all valid and ready. No index was found invalid or
unusable. `idx_scan` confirms active use of replay, sequence-verification,
trusted-snapshot and related indexes; zero scans on uniqueness indexes are not
grounds for dropping them because constraints require them.

## 5. Evidence Identity And Provider Scope

The exact evidence count and provider grouping were read from the actual
relation. All rows belong to the same measured provider scope:

| Provider | Provider symbol | Depth | Evidence rows |
|---|---|---:|---:|
| KRAKEN | PF_XBTUSD | 25 | 124,293,051 |

Evidence types and origins were measured previously and remain represented by
the current schema. The prior audit found the table contained Kraken provider
delta/snapshot and checkpoint snapshot evidence. This plan does not rerun a
full grouping scan beyond the exact row count because it is not required for
the physical strategy decision and would add load under severe disk pressure.

The current live subscription is separate from historical persistence:

```text
ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false
ORDER_BOOK_ACQUISITION_STARTUP_ENABLED=true
desired provider: KRAKEN
desired symbol:   BTC/USD / PF_XBTUSD
desired depth:    25
```

This preserves current live order-book acquisition while stopping the raw
historical evidence writer. The capture-disabled state must be rechecked again
immediately before any future cleanup window.

## 6. Dependency And Replay Integrity

### Actual retention tables

The actual schema is `public` and the relevant relations are:

- `public.historical_order_book_evidence`
- `public.historical_order_book_sequence`
- `public.historical_order_book_replay_artifact`
- `public.historical_order_book_evidence_dependency`
- `public.historical_order_book_evidence_segment`
- `public.historical_order_book_retention_hold`
- `public.historical_order_book_retirement_operation`

Foreign-key relationships are:

```text
evidence.market_id -> markets.market_id
evidence.sequence_id -> sequence.sequence_id
artifact.sequence_id -> sequence.sequence_id
dependency.artifact_id -> artifact.artifact_id
dependency.sequence_id -> sequence.sequence_id
dependency.segment_id -> segment.segment_id
hold.artifact_id -> artifact.artifact_id
hold.sequence_id -> sequence.sequence_id
hold.segment_id -> segment.segment_id
segment.market_id -> markets.market_id
segment.sequence_id -> sequence.sequence_id ON DELETE CASCADE
```

The evidence table itself has no direct foreign key from an evidence row to a
segment. Segment membership is an ordinal range in segment metadata. This is
important: without segment rows, evidence cannot be classified by the governed
segment model.

### Sequences

| Sequence status | Count | Evidence rows |
|---|---:|---:|
| `SEALED` | 1,759 | 94,332,776 |
| `INVALIDATED` | 110 | 29,960,275 |
| **Total** | **1,869** | **124,293,051** |

The evidence-row grouping by sequence was bounded through the existing
`sequence_id` index. No sequence is `ACTIVE` in the current measured state.
That does not make all sealed or invalidated sequences eligible: dependency,
hold, trusted-boundary and classification conditions still apply.

### Replay artifacts and dependencies

The current database contains:

```text
replay artifacts: 51
dependencies:     51
dependency state: 51 PROTECTED, 0 RELEASED
protected artifact count: 51
distinct protected sequence targets: 49
```

The protected dependencies are replay-artifact source protections. Artifact rows
contain serialized bid/ask payloads, source evidence fingerprints, sequence
range metadata, provider metadata, trust status and provenance fields. They are
not treated as a replacement for the source evidence by this plan because:

- the application explicitly protects the source sequence when an artifact is
  promoted;
- the artifact fingerprint and provenance contract references the source
  boundary;
- ADR-080 requires dependency release only after a durable successor result;
- no approved product decision has declared the payload alone sufficient for
  all historical reproducibility requirements.

Therefore all 51 dependency targets are classified
`PROTECTED_REQUIRED` until a human owner reviews them. Their payloads may make
them candidates for `PROTECTED_REVIEW_NEEDED`, but that classification cannot be
assigned automatically.

### Holds and operations

```text
active retention holds: 0
evidence segments:      0
retirement operations:  1 DRY_RUN / SELECTED
```

The existing `DRY_RUN` / `SELECTED` row is not proof that any evidence is
eligible. It records an earlier dry-run selection and must not be reused as
authorization for a future mutation. A future dry run must be revalidated by a
human against the current database state.

### Evidence classification

| Classification | Current quantity | Finding |
|---|---:|---|
| `PROTECTED_REQUIRED` | 51 dependencies, 51 artifacts, 49 distinct sequence targets | `MEASURED`; source ranges remain protected |
| `PROTECTED_REVIEW_NEEDED` | 0 assigned automatically | `UNVERIFIED`; human artifact/reproducibility review required |
| `UNPROTECTED_ELIGIBLE` | 0 | `MEASURED` at the governed-segment level because there are no segments and no released dependency |
| `UNCLASSIFIED` | 1,869 sequences / 124,293,051 evidence rows are not assigned to a segment | `MEASURED` plus schema mapping |
| `UNKNOWN` | Exact row-level protected/unclassified overlap | `NOT_MEASURED`; requires segment backfill and range mapping |

The sequence-level query identified 49 sequences with non-released dependency
coverage and 1,820 sequences without a directly inferred non-released dependency
target. The latter are not classified as eligible because segment metadata is
absent and ADR-080 requires unknown legacy evidence to fail closed.

No exact row-level protected/unprotected split is claimed. A single protected
artifact may cover an ordinal range within a sequence, and the current
sequence-level legacy candidate query is broader than the segment-range model.

## 7. Retention Architecture Assessment

### ADR-080 behavior

ADR-080 establishes:

- retention granularity is an explicit evidence segment;
- a trusted successor boundary is required;
- dependency release is required;
- an active hold blocks retirement;
- untrusted artifacts block retirement;
- unknown legacy evidence fails closed;
- storage pressure cannot bypass governance;
- logical segment deletion is authorized;
- physical partition retirement is future work;
- automatic destructive retirement is disabled by default.

The current database state matches the fail-closed design: there are no
segments, all dependencies are protected, and no eligible evidence was
identified.

### Existing service behavior

`OrderBookRetentionService` supports promotion, dependency protection/release,
holds, retirement preview and bounded retirement. The retirement service checks
sequence status, non-released dependencies, active holds, trusted artifacts and
released dependency presence before deleting an approved candidate.

`OrderBookRetentionService.previewRetirement()` is **not read-only**. It calls
`repository.recordDryRun(...)`, which inserts a row into
`historical_order_book_retirement_operation`. The HTTP `GET /preview` endpoint
therefore persists state. It was not invoked.

The scheduler also calls the preview path before any optional retirement. Even
when destructive retirement is disabled, an enabled scheduler would write a
dry-run operation. The scheduler and destructive-retirement gates are separate.

`OrderBookRetentionService.retire(...)` is destructive and additionally writes
mutation operation state. It was not invoked.

### Segment backfill behavior

`OrderBookEvidenceSegmentBackfillService` is a mutating operation despite its
diagnostic purpose. It:

- locks each selected sequence with `FOR UPDATE`;
- counts evidence rows for the sequence;
- discovers trusted snapshots as successor boundaries;
- inserts and closes segment rows;
- marks `backfill_completed_at` on the sequence;
- commits per sequence through a transaction template.

The service starts new legacy segments as `LEGACY_UNKNOWN`. It does not itself
prove or assign `LEGACY_CLASSIFIED`. It can create closed segments, but unknown
classification remains a blocker. It also performs per-sequence evidence
counts and boundary queries over a 124M-row table.

No backfill was executed.

### Backfill prerequisite and feasibility

Backfill is a prerequisite for applying the segment-level retention model to
the legacy relation. It is not sufficient for cleanup because classification,
trusted successor validation, dependency review and a released dependency are
still required.

Expected behavior from source code:

- canonical evidence rows are not changed;
- segment rows and sequence completion metadata are inserted/updated;
- one sequence is processed per transaction by default;
- default batch is one sequence and maximum is ten;
- default boundary batch is ten and maximum is 100;
- sequence rows are locked while each sequence is processed;
- no intentional large temporary file is created by the Java code;
- PostgreSQL may still use I/O, shared buffers and temporary work space for
  ordered boundary queries and index access.

Exact duration, WAL volume, lock wait behavior and temporary-space demand are
`UNVERIFIED` and must not be invented. The current 11 GB free space makes a
long-running diagnostic scan operationally unsafe to start without additional
capacity and a measured cancellation plan. The absence of segment metadata is
therefore an engineering and operational blocker, not a safe deletion shortcut.

## 8. Physical Reclamation Strategies

The table is a regular non-partitioned relation. Logical deletion and physical
filesystem reclamation must be treated as different operations.

| Strategy | Reclaimable GiB | Temporary disk needed | Downtime/locking | Data risk | Feasible now |
|---|---:|---:|---|---|---|
| A. Governed logical segment retirement | Unknown; potentially the deleted heap/index tuple space becomes reusable | Low to unknown; backfill/query I/O and WAL still require capacity | Bounded sequence row locks and delete transactions; table remains online | Low only after segment/dependency classification | No: zero segments, zero eligible evidence, no exact row classification |
| B. `VACUUM` after logical retirement | No predictable OS reclaim; reusable PostgreSQL pages only | Low to moderate I/O; exact demand unmeasured | Table remains online, but I/O pressure can be high | Low if run after approved deletion; does not solve filesystem immediately | Not useful as immediate physical recovery |
| B. `pg_repack` or equivalent online rewrite | Up to the retired relation/index size after approved filtering; exact reclaim depends on retained data | At least a second copy of retained heap/indexes plus WAL and safety headroom | Mostly online with brief final lock, but still requires shadow relation/indexes | Medium to high; extension/tool and rollback/backup requirements | No: no tool/extension verified and 11 GB is far below required headroom |
| C. Controlled replacement relation | Potentially most retained relation plus indexes, up to approximately 84.7 GiB if full rebuild | At least one retained copy plus new indexes; likely tens of GiB to >85 GiB | Long rewrite, final lock, FK/index cutover and downtime | High without a tested rollback and migration owner | No: insufficient capacity and no approved migration |
| D. Additional volume/tablespace | Depends on approved target relation; can provide physical headroom | Additional storage sized for shadow copy, WAL and rollback | Can reduce pressure, but still requires a controlled operation | Medium; infrastructure and restore risks | Yes only after infrastructure approval and capacity provisioning |
| E. Partitioned future design | Future partition drop can reclaim entire governed ranges | Capacity for migration/repartitioning; exact amount unknown | Migration/cutover required | High until proven; not current schema | No for immediate use; future architecture work required |
| F. Full historical retirement decision | Up to approximately 84.7 GiB relation space after a physical operation | At least backup/restore and rewrite/drop capacity | Planned downtime or controlled migration | High: all raw replay evidence is lost; 51 dependencies need coordinated decision | No: product and retention approvals plus capacity required |

### Strategy A: governed logical retirement

This is the only currently authorized data-deletion mechanism in ADR-080. It
would preserve segment metadata as `RETIRED` and delete evidence in bounded
batches. It does not immediately return the deleted heap/index pages to the
host filesystem. PostgreSQL can reuse the space internally, and later vacuum
can make pages available for reuse, but physical filesystem reclamation is not
guaranteed.

It is not executable now because:

- there are no evidence segments;
- legacy rows are unclassified;
- all dependencies are protected;
- no released dependency is present;
- exact row-level protected ranges are unknown.

### Strategy B: online PostgreSQL reclamation

`pg_repack` was not found in repository tooling, and no `pg_repack` extension is
installed in the database. Installation was not attempted. Even if approved,
an online rewrite needs a shadow relation and rebuilt indexes. With the current
relation at approximately 84.7 GiB and only 11 GB free, this is not feasible
on the current filesystem.

Dropping or rebuilding individual indexes is not an acceptable first step:

- the indexes support replay, provenance, identity and retention queries;
- dropping them would weaken constraints or runtime behavior;
- rebuilding one of the 8.5-14.4 GiB indexes also exceeds practical headroom;
- it would not reclaim heap space permanently unless followed by broader
  physical maintenance.

### Strategy C: controlled table reconstruction

A replacement relation could theoretically retain only approved evidence, build
the required indexes, validate foreign keys and switch ownership. This is not a
safe current procedure because the current filesystem cannot hold the source,
replacement, indexes, WAL and rollback reserve together. It also requires a
schema migration owner, downtime/cutover plan and verified restore path.

This strategy must not be attempted as an emergency table copy on the current
volume.

### Strategy D: additional storage or tablespace

This is the necessary prerequisite for physical reclamation. Supported options
depend on the deployment owner:

- attach a larger Docker-backed filesystem and move the PostgreSQL data volume;
- provision a PostgreSQL tablespace on a separately managed filesystem;
- provision temporary capacity for a controlled relation rewrite/repack and
  retain it through validation and rollback expiry;
- migrate the database to an approved larger PostgreSQL host.

No Docker volume or mount was modified. The minimum capacity must be based on a
future operation-specific sizing exercise. The current relation size alone is
not enough because WAL, indexes, backup, rollback and retained data all need
headroom.

### Strategy E: full historical retirement

If the product owner explicitly decides that no Kraken historical order-book
replay or research reproducibility must be retained, all PF_XBTUSD raw evidence
could become a decision scenario. That would lose:

- deterministic reconstruction of historical order-book state;
- source provenance for 51 protected replay artifacts;
- the ability to recompute or independently verify those artifacts from raw
  evidence;
- historical diagnostics for the 1,869 sequences.

The 51 replay artifacts could remain as serialized payloads, but their current
dependencies explicitly protect source evidence. They cannot be declared
self-sufficient without a human-approved reproducibility contract and a
coordinated dependency transition. Full retirement would therefore require a
new approved retirement policy or an explicit ADR decision if it changes the
current ADR-080 contract.

## 9. Recommended Strategy

### Recommendation

`ADDITIONAL_STORAGE_REQUIRED` is the dominant verdict.

Do not attempt cleanup on the current 11 GB free filesystem. First provision
temporary or permanent additional capacity, then perform a separate approved
retention and physical-reclamation change window.

The recommended sequence is:

1. Immediate operations: keep raw capture disabled, preserve live order-book
   acquisition, monitor filesystem pressure and obtain additional capacity.
2. Governance: confirm whether Kraken raw history must be retained at all;
   review the 51 artifacts and 51 protected dependencies without releasing any.
3. Classification: after capacity and approval, run a separately controlled,
   bounded segment backfill. Treat all generated legacy segments as
   `LEGACY_UNKNOWN` until provenance classification is approved.
4. Eligibility: classify only ranges with sufficient trusted boundaries and
   complete dependency/hold review. Do not infer eligibility from age or sealed
   status alone.
5. Logical retirement: use the existing bounded retention path only for exact
   approved eligible segments, with fresh dry-run evidence and human approval.
6. Physical recovery: use a capacity-backed PostgreSQL strategy. Prefer a
   future partitioned design for recurring retention; for this legacy table,
   use an approved online rewrite/repack or controlled replacement only after
   capacity, backup and rollback requirements are proven.
7. Prevention: define a bounded Kraken historical retention policy and keep
   capture disabled unless a specific research Story enables it.

### Expected physical reclaim

The only defensible upper bound is approximately 84.7 GiB, the current total
relation size, if all raw evidence and its indexes were approved for full
physical removal. The expected currently authorized reclaim is **0 GiB**:

- no evidence is `UNPROTECTED_ELIGIBLE`;
- no segment exists;
- no physical rewrite is feasible with 11 GB free;
- logical deletion has not been authorized or executed.

The physically reclaimable amount after a future approved operation is
`UNKNOWN`, not 85 GiB, until exact retained ranges, relation layout, rewrite
method and backup/rollback reserve are defined.

## 10. Non-Destructive Operational Runbook

This runbook is a future reviewed procedure. It is not authorization to execute
the destructive phases.

### 10.1 Preconditions

All must be satisfied before any mutation:

- explicit human approval naming the provider, symbol, depth, evidence range,
  operation and maintenance window;
- approved Engineering Story or operational change record;
- verified backup and restore test for `market_data`, including the evidence
  relation and retention tables;
- additional filesystem capacity sized for the chosen physical method;
- documented rollback reserve and WAL capacity;
- current `ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false` verified immediately
  before the window;
- live order-book acquisition behavior and current market-price behavior
  verified as required by the owner;
- Market Data and PostgreSQL health checks green;
- exact dependency/artifact review completed for all 51 protected rows;
- segment classification completed, reviewed and fingerprinted;
- fresh retention preview reviewed by a human;
- no active retention hold or concurrent replay/retention operation;
- cTrader OHLC collection and Market Intelligence health baseline recorded;
- operations owner present with a stop authority.

Backup availability is currently **UNVERIFIED**. No repository backup/restore
script or backup artifact was found, and no backup command was run. Execution
must stop until the database owner provides and tests the restore path.

### 10.2 Safe preflight commands

The following commands are read-only diagnostics. They are examples for a
future maintenance window and are safe only with the stated timeouts and output
limits. They do not authorize cleanup.

Filesystem and Docker status:

```bash
df -hT /
docker system df
docker inspect market_data-postgres --format '{{range .Mounts}}{{.Source}} -> {{.Destination}} rw={{.RW}}{{"\n"}}{{end}}'
```

PostgreSQL identity and size:

```bash
docker exec market_data-postgres psql -U postgres -d market_data \
  -v ON_ERROR_STOP=1 -c "\
  SET statement_timeout = '10s'; \
  SELECT current_database(), current_user, version(); \
  SELECT pg_database_size(current_database()), \
         pg_total_relation_size('public.historical_order_book_evidence'::regclass), \
         pg_relation_size('public.historical_order_book_evidence'::regclass), \
         pg_indexes_size('public.historical_order_book_evidence'::regclass);"
```

Relation and partition identity:

```bash
docker exec market_data-postgres psql -U postgres -d market_data \
  -v ON_ERROR_STOP=1 -c "\
  SET statement_timeout = '10s'; \
  SELECT n.nspname, c.relname, c.relkind, c.relispartition, \
         pg_get_userbyid(c.relowner), pg_total_relation_size(c.oid) \
    FROM pg_class c \
    JOIN pg_namespace n ON n.oid = c.relnamespace \
   WHERE c.oid = 'public.historical_order_book_evidence'::regclass; \
  SELECT inhrelid::regclass \
    FROM pg_inherits \
   WHERE inhparent = 'public.historical_order_book_evidence'::regclass;"
```

Index definitions and sizes:

```bash
docker exec market_data-postgres psql -U postgres -d market_data \
  -v ON_ERROR_STOP=1 -c "\
  SET statement_timeout = '10s'; \
  SELECT indexrelid::regclass, pg_relation_size(indexrelid), idx_scan, \
         indisvalid, indisready \
    FROM pg_stat_user_indexes \
    JOIN pg_index ON pg_index.indexrelid = pg_stat_user_indexes.indexrelid \
   WHERE relid = 'public.historical_order_book_evidence'::regclass \
   ORDER BY pg_relation_size(indexrelid) DESC;"
```

Dependency state without payload scans:

```bash
docker exec market_data-postgres psql -U postgres -d market_data \
  -v ON_ERROR_STOP=1 -c "\
  SET statement_timeout = '10s'; \
  SELECT state, count(*) \
    FROM public.historical_order_book_evidence_dependency \
   GROUP BY state ORDER BY state; \
  SELECT count(*) FROM public.historical_order_book_evidence_segment; \
  SELECT released_at IS NULL AS active, count(*) \
    FROM public.historical_order_book_retention_hold \
   GROUP BY released_at IS NULL;"
```

### 10.3 Explicitly non-authorized operations

The following are intentionally documented but must not be executed as part of
this plan without a separate approved change:

```text
NOT AUTHORIZED — DO NOT EXECUTE:
DELETE FROM public.historical_order_book_evidence ...;
TRUNCATE public.historical_order_book_evidence ...;
DROP TABLE public.historical_order_book_evidence ...;
ALTER TABLE public.historical_order_book_evidence ...;
VACUUM FULL public.historical_order_book_evidence;
REINDEX TABLE public.historical_order_book_evidence;
CLUSTER public.historical_order_book_evidence ...;
POST /internal/v1/order-book/retention/backfill
GET  /internal/v1/order-book/retention/preview
POST /internal/v1/order-book/retention/retire
docker system prune ...
docker volume rm ...
```

The retention preview endpoint is included in the prohibited list because the
current implementation inserts a `DRY_RUN` operation record. The backfill
endpoint is prohibited because it locks sequences and writes segment metadata.

### 10.4 Intended execution phases after approval

#### Phase 1: freeze and verify

Purpose: prevent new raw evidence and establish a stable baseline.

Preconditions: approved window, capture-disabled state, backup and capacity.

Expected effect: no database mutation; live acquisition remains available.

Stop conditions: capture enabled, active sequence appears, filesystem falls
below the agreed emergency threshold, backup restore fails, or PostgreSQL
health degrades.

Validation: repeat the safe preflight commands, check current process health,
confirm live order-book and price endpoints, and record relation sizes.

Rollback: restore the prior operational configuration only through a separate
approved change; no configuration change is made by this plan.

#### Phase 2: dependency and segment preparation

Purpose: establish governed ranges without changing canonical evidence.

Expected effect: segment metadata and sequence backfill timestamps may be
written by the approved backfill operation.

Failure conditions: long lock waits, statement timeouts, sequence inconsistency,
unexpected provider/symbol/depth, filesystem pressure or any evidence mutation.

Stop conditions: any generated segment is not `LEGACY_UNKNOWN` when provenance
is insufficient, or any protected artifact/dependency mapping is ambiguous.

Validation: compare segment ranges against evidence ordinals, verify trusted
successor evidence, confirm counts, and record the operation audit trail.

Rollback: metadata backfill is not assumed reversible. A tested database backup
and restore is required before starting. Do not delete or rewrite metadata to
simulate rollback.

#### Phase 3: human classification and eligibility review

Purpose: classify only ranges supported by provenance and retention policy.

Expected effect: classification/dependency decisions may change metadata only
through an approved service or migration; no dependency is released by this
plan.

Stop conditions: any age-only classification, missing successor, untrusted
artifact, unresolved dependency, active hold or mismatch between artifact range
and segment range.

Validation: produce a signed/fingerprinted inventory with counts for
`PROTECTED_REQUIRED`, `PROTECTED_REVIEW_NEEDED`, `UNPROTECTED_ELIGIBLE`,
`UNCLASSIFIED` and `UNKNOWN`.

Rollback: dependency release is not automatically reversible under the current
contract. Do not execute it without an explicit release decision and successor
artifact evidence.

#### Phase 4: governed logical retirement

Purpose: delete only exact approved eligible evidence ranges through the
existing bounded retention control.

Expected effect: evidence rows are deleted in configured batches; segment state
becomes `RETIRING` and then `RETIRED`; operation records are written.

Failure conditions: dry-run fingerprint mismatch, protection/hold detection,
untrusted artifact, sequence lock timeout, batch failure or unexpected row
count.

Stop conditions: any row count outside the reviewed batch, any deletion from a
protected range, filesystem pressure, or replay/health degradation.

Validation: verify operation status, affected counts, segment state, dependency
state, foreign-key integrity and replay failure behavior for retired ranges.

Rollback: row deletion is not reversible without a verified backup/restore.
Logical retirement must therefore be treated as irreversible in the live
database.

#### Phase 5: physical reclamation

Purpose: return unused relation/index storage to the filesystem.

Expected effect: depends on approved method: partition drop, online repack or
controlled replacement. No method is selected for execution by this plan.

Failure conditions: insufficient shadow capacity, WAL exhaustion, lock timeout,
backup failure, failed index validation, failed FK validation or application
health degradation.

Stop conditions: available capacity below the calculated reserve, unexpected
relation/index size, inability to hold rollback copy, or any failed validation.

Validation: exact relation/index sizes, database size, filesystem free space,
PostgreSQL integrity, application health, replay behavior and live market-data
behavior.

Rollback: only possible through the verified backup/restore or a retained old
relation during a controlled cutover. Do not claim rollback after dropping or
deleting the only copy.

## 11. Post-Operation Validation

After any approved future operation, all of the following must pass:

### Database

- PostgreSQL accepts connections.
- `pg_catalog` reports expected relation/index state.
- No invalid or not-ready indexes exist.
- Foreign-key and unique constraints remain present and valid.
- Retention operation rows match the approved fingerprint and affected count.
- Segment status and classification match the reviewed inventory.
- Database and relation sizes are measured again.
- No unexpected long-running transaction or lock remains.

### Replay and research

- Protected artifacts remain readable and fingerprint-valid.
- Replay of retained evidence succeeds for a bounded approved sample.
- Replay for retired evidence fails explicitly rather than returning a partial
  or substituted result.
- Market Intelligence reports the expected artifact/dependency state.
- cTrader OHLC capture continues independently of Kraken historical cleanup.

### Live Trading OS functionality

- Broker account synchronization remains healthy.
- Kraken balances and positions remain visible.
- Kraken current market prices remain fresh.
- Live order-book subscription remains active if still required.
- No broker credentials or service authorization behavior changed.
- Gateway and service health endpoints remain healthy.

### Operations

- Filesystem free space increased by the measured amount.
- Docker volume remains mounted and writable.
- PostgreSQL logs show no recovery, WAL or storage errors.
- Market Data and Market Intelligence logs show no new retention or replay
  failures outside the approved impact.
- Backup/restore evidence and operation fingerprints are archived with the
  change record.

## 12. Rollback And Recovery

### Before logical deletion

Rollback is possible only if a verified backup or retained source copy exists.
Metadata backfill and classification writes must be included in the backup
scope. A restore must be tested into an isolated PostgreSQL instance before the
live window.

### After logical deletion

Deleted evidence cannot be reconstructed from the remaining table. PostgreSQL
rollback is not provided by the transaction after commit. Recovery requires
restoring the affected database or relation from a verified backup and then
replaying any approved post-backup changes.

### During a physical rewrite

Rollback depends on retaining the original relation or a restorable backup until
all application and size validations pass. A failed `pg_repack` or replacement
cutover must not be handled by deleting the old relation first.

### After physical removal

Filesystem space reclamation is not reversible by PostgreSQL. Recovery requires
a verified backup restore and a controlled application reconciliation. No
rollback claim is valid until the restore path has been exercised.

## 13. Required Human Approvals

Before implementation or execution, obtain explicit approval for:

- the decision to retain, reduce or fully retire Kraken raw order-book history;
- whether the 51 protected replay artifacts require raw-source
  reproducibility;
- classification of legacy evidence and treatment of unknown ranges;
- dependency release, if ever proposed;
- the retention horizon and future capture policy;
- additional storage/tablespace or PostgreSQL migration;
- backup, restore and rollback evidence;
- the exact provider/symbol/depth/range predicate;
- maintenance window, downtime and lock thresholds;
- the chosen physical reclamation method;
- post-operation validation and acceptance owner;
- any new ADR or Engineering Story required by a change to ADR-080 behavior.

## 14. Potential Engineering Stories

No Story was created. The minimum likely work is:

### Story candidate 1: Legacy order-book segment classification

Implement a controlled, resumable classification workflow for existing
`LEGACY_UNKNOWN` evidence. It must preserve fail-closed behavior, emit a
reviewable inventory and avoid age-only eligibility. This is required before
the current legacy dataset can use segment-level retirement.

### Story candidate 2: Physical reclamation for governed ranges

Define and implement the minimum supported physical strategy for this
non-partitioned legacy table, including capacity planning, lock behavior,
backup/rollback, index validation and post-operation observability. Do not
build a generic storage-management framework.

### Story candidate 3: Bounded Kraken historical storage policy

Document and enforce the approved future capture/retention horizon for Kraken
order-book evidence, while preserving live order-book state and portfolio
visibility. Include a regression test proving that disabling historical capture
does not disable live acquisition.

### Story candidate 4: Storage observability and emergency thresholds

Add measured relation/index growth, WAL/headroom, capture state, segment
progress and retention-operation metrics sufficient to prevent another silent
unbounded-growth period. This should remain focused on the existing order-book
boundary.

## 15. Unresolved Questions

- Is there an independently verified backup of the `market_data` Docker volume?
- Has a restore of the evidence and retention tables been tested recently?
- Must the 51 serialized replay artifacts remain reproducible from raw source?
- Which exact evidence ordinal ranges do the 51 artifacts cover after segment
  mapping?
- Are any external consumers absent from the repository dependent on raw
  PF_XBTUSD evidence?
- Should all Kraken raw evidence be retired, or should a bounded diagnostic
  horizon remain?
- Which additional-storage option is supported by the deployment owner?
- What minimum free-space reserve is required for PostgreSQL, WAL, Docker and
  normal Trading OS operation?
- Is a future partitioned relation acceptable under current migration and
  ownership boundaries?
- What is the approved downtime/lock budget for physical reclamation?
- Does the current environment have a supported `pg_repack` installation path,
  or must the operation use a managed PostgreSQL migration?
- What validation proves that live order-book functionality remains useful after
  raw historical capture stays disabled?

## 16. Final Verdict

### `ADDITIONAL_STORAGE_REQUIRED`

This is the dominant verdict because:

- only 11 GB is free on the host filesystem;
- the target relation is approximately 84.7 GiB including indexes;
- the table is not partitioned;
- no safe physical rewrite or online repack can be staged with current space;
- the backup/restore path is unverified;
- no evidence segment is currently classified as eligible;
- all 51 dependency rows remain protected.

Secondary blockers are `DEPENDENCY_REVIEW_REQUIRED` and likely
`ENGINEERING_CHANGES_REQUIRED` for legacy segment classification and a
repeatable physical-retention strategy. They do not replace the dominant
capacity verdict.

The expected currently reclaimable filesystem capacity is **0 GiB approved**.
The theoretical upper bound after a full approved physical retirement is
approximately **84.7 GiB**, but the actual future reclaim is unknown until
retention scope, backup, capacity, method and retained evidence are approved.

## 17. Audit Trail

Read-only actions performed:

- inspected ADR-080, migrations, retention service, repository, scheduler,
  properties, backfill service and storage-health implementation;
- inspected Docker container status, Docker storage summary and PostgreSQL data
  volume mapping;
- connected read-only to `market_data` in `market_data-postgres`;
- measured database, relation, heap, index and TOAST sizes;
- counted evidence, sequence, artifact, dependency, segment, hold and operation
  rows;
- grouped sequence status, provider scope and dependency state;
- inspected constraints, foreign keys, indexes, index validity and usage
  counters;
- inspected PostgreSQL statistics and available extensions;
- confirmed no retention preview/backfill/retirement endpoint was invoked.

Not performed:

- no mutation or destructive SQL;
- no segment backfill;
- no retention preview endpoint, because it writes a dry-run row;
- no `EXPLAIN ANALYZE`, `VACUUM`, `REINDEX`, `CLUSTER`, `pg_repack`, Docker
  cleanup or volume operation;
- no backup, restore, export, service restart or broker operation.
