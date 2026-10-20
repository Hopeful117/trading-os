# Runtime Validation - Order Book Recovery and Retention

**Date:** 2026-10-20  
**Validation status:** `FIX_REQUIRED_BEFORE_CONTINUING`  
**Validation mode:** bounded runtime observation; no data deletion  

## Executive Verdict

The merged runtime was not initially deployed. The running `market-data` image
had only Flyway versions `V1..V10`, while the merged fix requires `V11..V13`.
After a controlled rebuild and restart of `market-data`, migrations `V11..V13`
were applied successfully and a new trusted active sequence was created.

Collection now persists new evidence and reports healthy capture state. This is
not sufficient for multi-day collection: retention has not executed and the
replay endpoint timed out while scanning the complete 36M-row evidence table.
The filesystem is currently safe, but continued capture would still grow the
20GB evidence table without observed retirement.

## Repository Baseline

| Item | Result |
|---|---|
| Branch | `main` |
| HEAD | `e89f775930907a3582e36857094f85d1269df13f` |
| Merge | `e89f7759 Merge pull request #153 from Hopeful117/story/0138-bounded-order-book-retention` |
| Fix commit | `99fee1b6 feat: add bounded order book evidence retention` |
| Working tree | Pre-existing untracked `docs/` artifacts; this report is added as a new untracked artifact |
| ADR | `docs/architecture/adr/ADR-067.md`, Accepted |
| Story | `docs/architecture/stories/0138-bounded-order-book-evidence-retention/story.md`, In Progress |
| Migrations | `V11__order_book_retention_lifecycle.sql`, `V12__order_book_replay_artifact_provenance.sql`, `V13__order_book_retirement_progress.sql` |
| Retention code | `OrderBookRetentionService`, `OrderBookRetentionRepository`, `OrderBookStorageHealthService`, `OrderBookStoragePressureGuard` |
| Tests | `OrderBookRetentionServiceTest`, `OrderBookRetentionPersistenceTest`, capture and security integration tests |

DevLog applicability was checked but unavailable because the provider returned
an exhausted-quota error. Repository and current runtime evidence were used.

## Merged Semantics

- Active sequences remain protected.
- Sealed sequences remain replayable while required by the trusted replay boundary.
- Invalid sequences remain diagnostically retained for the configured 30-day period.
- Unknown dependency/provenance metadata is protected by default.
- Replay artifacts, dependencies, and retention holds protect their evidence.
- Retirement requires a recorded dry-run, released dependencies, no active hold,
  a non-active sequence, and configured destructive retirement.
- Destructive retirement is disabled by default.
- Capture fails closed when storage pressure or durable persistence confirmation
  fails.
- The deployed controller exposes health, dependency, and hold operations, but
  no runtime endpoint or scheduler was found for `previewRetirement()` or
  `retire()`. No retirement operation was observed.

## Runtime Deployment and Storage

The initial runtime container was recreated after confirming 94GB free space.
No volume, table, row, partition, or research data was deleted.

### Initial operational state

Before redeployment, host and database storage were:

- Filesystem: `449G` total, `333G` used, `94G` available, `79%` used.
- Inodes: `11%` used.
- Order Book evidence: `36,191,796` rows, approximately `20GB`.
- Latest evidence: `2026-10-20T17:33:06.219Z`.
- Latest known-at: `2026-10-20T17:34:22.446002Z`.
- PostgreSQL accepted reads, but application logs contained repeated
  `could not write init file` and `database system is in recovery mode` errors.
- Runtime Flyway schema was only at version `10`; retention tables did not exist.

### Post-fix deployment

- `docker compose build market-data` completed successfully.
- `docker compose up -d market-data` recreated only `market-data`.
- Flyway applied versions `11`, `12`, and `13` successfully.
- PostgreSQL remained on the existing volume and existing evidence remained
  untouched.

## T0 Snapshot

T0 endpoint snapshot: `2026-10-20T20:20:25Z`.  
The nearest database snapshot was captured at `2026-10-20T20:18:34Z`.

| Metric | T0 |
|---|---|
| Filesystem | `449G` total, `334G` used, `92G` free, `79%` |
| Inodes | `11%` used |
| PostgreSQL | read/write, `pg_is_in_recovery=false`, `SELECT 1` successful |
| Evidence table | `20,064,597,504` bytes, approximately `20GB` |
| Evidence rows | `36,212,438` |
| Latest evidence | `2026-10-20T20:16:50.242Z` |
| Active sequence | `7fef3516-6c9c-4842-aed9-5d1ef27d89f3` |
| Market/depth | `e23ba62a-995f-4755-8deb-11a71ab3788b`, depth `25` |
| Sequence state | `ACTIVE`, valid, local ordinal `33,288` at health read |
| Valid/invalid/other sequences | `1 ACTIVE`, `66 INVALIDATED`, `51 SEALED` |
| FeatureSnapshots | `98`, latest cutoff `2026-10-20T20:00:00Z` |
| Outcomes | `641`, latest cutoff `2026-10-20T19:00:00Z`; `406 AVAILABLE`, `234 INCOMPLETE`, `1 UNAVAILABLE` |
| Replay readiness | `66` checks, `0` available, `0.0` coverage |
| Fee readiness | `66` checks, `0` available, `0.0` coverage |
| Retention artifacts/dependencies/holds | `0 / 0 / 0` |
| Retention operations | `0` |

The retention health endpoint returned:

- `captureState=HEALTHY`;
- `underPressure=false`;
- `durabilityCompromised=false`;
- `usableBytes=98,668,945,408`;
- `minimumFreeBytes=10,737,418,240`;
- `lastSuccessfulPersistence=2026-10-20T20:20:42.724630164Z`.

## Provider and Recovery

Provider recovery succeeded after the controlled restart:

- Kraken Futures WebSocket connected at `2026-10-20T20:16:03.476Z`.
- Subscription restored for `PF_XBTUSD`, market `BTC/USD`, depth `25`.
- New sequence created at `2026-10-20T20:16:03.940Z`.
- First snapshot occurred at `2026-10-20T20:16:03.542Z` and was persisted.
- Evidence persistence advanced through the observation window.
- No reconnect loop was observed after the restart.

## T+15m Checkpoint

The checkpoint was captured at `2026-10-20T20:39:28Z`, approximately 21
minutes after the nearest database T0. It is used as the requested approximate
15-minute checkpoint because the replay smoke test was performed between T0 and
the observation interval.

| Metric | T+15m approximate |
|---|---|
| Filesystem | `449G` total, `334G` used, `92G` free, `79%` |
| Inodes | `11%` used |
| Evidence rows | `36,425,789` |
| Evidence table | `21,191,426,048` bytes, approximately `20GB` |
| Latest evidence occurred-at | `2026-10-20T20:25:51.700Z` |
| Latest known-at | `2026-10-20T20:39:29.153594Z` |
| Active sequence | `7fef3516-6c9c-4842-aed9-5d1ef27d89f3`, still `ACTIVE` |
| Active local ordinal | `233,961` |
| Capture health | `HEALTHY`, no pressure, no durability compromise |
| FeatureSnapshots | `98`, unchanged; expected cadence is hourly |
| Outcomes | `641`, latest computed at `2026-10-20T20:12:35.487649Z` |
| Replay readiness | `0.0%` |
| Fee readiness | `0.0%` |
| Retention executions | `0` |
| Retired/compacted evidence | `0` |

The active sequence health endpoint at the checkpoint reported
`lastSuccessfulPersistence=2026-10-20T20:39:28.937149826Z`.

## Collection Velocity and Storage Growth

Using the nearest T0 database snapshot (`20:18:34Z`) and T+15m snapshot
(`20:39:29Z`):

- New rows: `213,351` over approximately `20.9 minutes`.
- Logical write velocity: approximately `612,000 rows/hour`.
- Logical table growth: `126,828,544` bytes over approximately `20.9 minutes`.
- Approximate physical table growth: `0.36 GB/hour`.
- The observed post-fix rate is at least as high as the historical rate; no
  retention offset was observed.

The table grew during the window and no retirement operation ran. This is
currently transient only in the sense that the retention horizon has not been
reached; there is no runtime evidence that the lifecycle can reach a bounded
steady state.

## Retention Validation

### Result

`RETENTION_BROKEN`

The runtime schema is now present and the health guard works, but retention did
not execute:

- `historical_order_book_retirement_operation` contains zero rows.
- No scheduler invokes `previewRetirement()` or `retire()`.
- The retention controller does not expose preview or retirement endpoints.
- No evidence was retired or compacted.
- Existing pre-fix evidence remains preserved, with no dependency/artifact
  metadata and therefore no observed eligibility path.

Active-sequence protection is correct in the observed state: the current active
sequence remained present and capture continued. Replay-required evidence was
not directly testable because no promoted artifact or dependency exists, but
unknown dependency state remains protected by the schema/design.

Invalidated sequences were preserved; the latest invalidated sequence from the
incident remains in the database. No destructive operation was attempted.

### Storage projection

At the observed post-fix rate, unbounded growth would be approximately
`8.6GB/day`, with a rough host time-to-full of about `10-11 days` from 92GB
free. This is not a valid steady-state projection because the merged design
requires lifecycle retirement, but it demonstrates why sustained collection
cannot be approved before retirement execution is observable.

PostgreSQL checkpoints succeeded and no `VACUUM FULL` or destructive cleanup was
run. Physical table size therefore remains the relevant operational measure.

## Replay Validation

### Result

`REPLAY_BROKEN`

Two bounded replay requests were made against newly collected evidence, using
the trusted service JWT path and cutoffs `2026-10-20T20:16:40Z` and
`2026-10-20T20:16:04Z`. Neither returned within 20/60 seconds.

The database showed the replay query scanning the full evidence population. The
request was cancelled after timeout. `market-data` logged the exact query
timeout and stack path through `OrderBookEvidenceRepository.findTrusted()`.

This is not a trust-semantics failure: the new sequence and evidence are
present, but the current replay query is not operationally usable against the
existing 36M-row table. Replay readiness therefore remains `0%`; it cannot be
classified as population lag because an eligible post-fix sequence exists and
the authoritative replay call timed out.

Replay after retention was not attempted because no retention operation ran.

## Research Pipeline

### Integrity endpoint

The authenticated endpoint returned `status=HEALTHY` at
`2026-10-20T20:39:49.318039151Z`:

- acquisition status: `HEALTHY`;
- latest snapshot cutoff: `2026-10-20T20:00:00Z`;
- lag: `0` seconds;
- timestamp anomaly count: `0`;
- overdue outcomes: `0`;
- warning: `multiple_outcome_records` for the configured market.

### Readiness endpoint

The authenticated endpoint returned:

- `featureSnapshotCount=98`;
- `approximateAvailableResearchPopulation=265`;
- `replayChecks=66`, `replayAvailable=0`, `replayCoverage=0.0`;
- `feeChecks=66`, `feeAvailable=0`, `feeCoverage=0.0`;
- readiness `false`;
- reasons include replay coverage, fee coverage, signal counts and period length.

The `0%` replay result is classified as `REPLAY_PIPELINE_FAILURE`, not
`EXPECTED_POPULATION_LAG`, because the direct trusted replay endpoint timed out
against an existing eligible sequence.

FeatureSnapshots and outcomes were not expected to advance during a 21-minute
window when the configured observation cadence is one hour. Existing research
rows remained intact.

Canonical regimes reported by readiness were `TRANSITIONING` and `TRENDING`;
the endpoint reported `33` signals, `16` long and `17` short. No additional
distribution query was necessary for this operational gate.

## Fees

### Result

`FEE_CONFIGURATION_BLOCKED`

The following runtime parameters are unset:

- `RESEARCH_LIFECYCLE_FEE_COVERAGE_BROKER_ACCOUNT_ID`;
- `RESEARCH_LIFECYCLE_FEE_COVERAGE_PROVIDER`;
- `RESEARCH_LIFECYCLE_FEE_COVERAGE_PRODUCT_TYPE`;
- `RESEARCH_LIFECYCLE_FEE_COVERAGE_INSTRUMENT`.

Fee readiness is `0/66`, with `fee_coverage_incomplete`. No authoritative
historical fee source can be resolved until a human configures these values.
No guessed or fallback fee was introduced.

## Logs and Errors

### Before redeployment

- Repeated PostgreSQL `could not write init file` errors.
- PostgreSQL recovery-mode connection failures.
- Hikari pools became empty and research scheduled tasks failed.
- The old runtime evidence stopped at the incident timestamp.

### After redeployment and during validation

- No new `evidence persistence failure` was observed.
- No new `could not write init file` or PostgreSQL recovery error was observed.
- Two replay query timeout errors were observed, both caused by the validation
  requests being cancelled after exceeding the timeout.
- Startup emitted standard Spring warnings about generated development password,
  Open Session in View, and default load-balancer cache.
- No retention failure was observed because no retention execution occurred.

## Acceptance Gates

| Gate | Result | Evidence |
|---|---|---|
| Collection | `COLLECTION_DEGRADED` | Provider, sequence creation, persistence and health recovered; research cadence was not observed for a full hour and historical runtime errors were present before redeploy |
| Retention | `RETENTION_BROKEN` | No retirement operation, no scheduler/preview route, table growth continued |
| Replay | `REPLAY_BROKEN` | Trusted replay endpoint timed out on the current 36M-row dataset |
| Fees | `FEE_CONFIGURATION_BLOCKED` | All four required fee parameters unset; readiness `0/66` |

## Overall and Product Gates

**Overall verdict:** `STOP_COLLECTION`

The system should not be left collecting for several days. Current free space
is adequate for short controlled recovery observation, but storage remains
unbounded in the deployed runtime and trusted replay is not operational at the
current population size.

**Product-development gate:** `NOT_READY_FOR_PRODUCT_DEVELOPMENT`

Post-trade Decision History V1 should not start while retention and replay are
still operational blockers.

## Longer Checks

Do not run these as part of the current blocked validation. After fixing replay
and exposing/activating the reviewed retention workflow, capture the same
bounded snapshot at:

- T+6h: `df -h /`, `df -i /`, PostgreSQL evidence table size/row count, active
  sequence, retirement operation counts, replay readiness, fee readiness,
  integrity endpoint, and error counts.
- T+24h: repeat T+6h and compare logical live rows with physical table size;
  verify that retirement progress is resumable and storage growth is bounded.

## Findings

### P0

- None observed during this bounded validation.

### P1

- Runtime was not deployed with merged migrations until controlled redeployment.
- Retention has no runtime execution path in the deployed controller and no
  scheduler; evidence continues growing without observed retirement.
- Replay query times out while scanning the existing 36M-row evidence table,
  preventing trusted replay validation and leaving readiness at `0%`.

### P2

- Fee configuration remains unset and independently blocks readiness.
- Integrity reports `multiple_outcome_records` for the configured market.
- Standard startup warnings remain, including generated development password and
  default Open Session in View.

## Runtime Safety Summary

- Is sustained multi-day collection safe? **No.**
- Can Order Book capture remain running? **Only for short controlled observation; not approved for multi-day collection.**
- Is storage now credibly bounded? **No. Retention did not execute.**
- Is replay collecting useful evidence? **Evidence is collecting, but trusted replay is operationally blocked.**
- Are fees still blocking readiness? **Yes, separately; all required parameters are unset.**
- Can Post-trade Decision History begin? **No, not before retention and replay blockers are fixed.**

## Modifications and Mutations

- Repository report artifact created at this path.
- Runtime `market-data` image rebuilt from merged source.
- Runtime `market-data` container recreated once.
- Flyway `V11..V13` applied to the existing PostgreSQL database.
- No source code changes made during validation.
- No configuration values changed.
- No rows, tables, volumes, partitions, or research evidence deleted.
- Two long-running replay validation queries were cancelled; no data mutation
  resulted.

## Exact Next Action

Stop sustained collection. Fix the replay query path so a bounded trusted replay
can complete against the existing evidence population, and expose or otherwise
activate the reviewed dry-run/resumable retirement workflow. Then configure the
four authoritative fee parameters separately and repeat this runtime validation
before allowing multi-day collection or starting Post-trade Decision History V1.

## Final Operational Answers

1. **Is Order Book collection actually progressing after the fix?** Yes. After the controlled redeployment, Kraken connected, a new active sequence was created, and `213,351` new rows were persisted during the bounded observation window.
2. **Is retained storage now credibly bounded?** No. No retention operation executed and the evidence table grew by approximately `127MB` during the window.
3. **Can newly collected evidence be used for trusted replay?** Not operationally. The replay endpoint timed out scanning the existing 36M-row population.
4. **Is fee resolution healthy or still separately blocked?** Still separately blocked. All four required runtime fee parameters are unset and fee coverage is `0/66`.
5. **Can Trading OS safely collect data in the background while development moves to Post-trade Decision History V1?** No. Stop sustained collection until retention execution and replay performance are fixed and revalidated.
