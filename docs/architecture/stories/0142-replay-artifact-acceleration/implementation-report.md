# Implementation Report - Story 0142

## Decision

**ARTIFACT-FIRST REPLAY PATH IMPLEMENTED; REVIEW FINDINGS ADDRESSED**

The repository already implements checkpoint-aware strict replay. Measurements
confirm that it materially reduces the canonical evidence scan without requiring a
new source, schema, cache authority, or ADR-069 boundary change. This reduction is
not sufficient by itself to resolve the Story's target replay-performance problem.
The existing promoted-artifact persistence path is now consumed when callers provide
an optional semantic identity. PostgreSQL-backed Testcontainers validation now
exercises promoted artifact reads and lifecycle rejection paths. A representative
 controlled artifact performance measurement now covers a promoted replay with 10001 evidence rows.

## Baseline

The controlled Market Data PostgreSQL database contained approximately:

| Measure | Value |
|---|---:|
| Estimated evidence rows | `49,750,592` |
| Evidence table total size | `28 GB` |
| Checkpoint snapshots | `877` |
| Promoted replay artifacts | `0` |
| Evidence dependencies | `0` |
| Active retention holds | `0` |

The checkpoint sample covered the bounded market/depth scope with checkpoints
approximately every five minutes. No active sequence was present when the final
inventory was captured; sealed and invalidated sequence behavior remained covered
by the existing persistence contract.

## Checkpoint Candidate Measurement

For a representative sealed sequence with approximately `257,423` evidence rows,
the latest checkpoint before the measured cutoff was at ordinal `220,862`.

| Replay start | Returned rows | Execution time |
|---|---:|---:|
| Provider snapshot, ordinal `0` | `256,213` | `654 ms` |
| Latest checkpoint, ordinal `220,862` | `35,346` | `218 ms` |

The existing checkpoint path therefore reduced the evidence scan by approximately
`86%` and the measured query time by approximately `67%`, while preserving the
same sequence and cutoff boundaries. The existing repository test
`replayReadStartsAtLatestCheckpointAndExcludesFutureEvidence` covers checkpoint
selection and future-evidence exclusion.

No new checkpoint implementation was required.

## Promoted Artifact Candidate Measurement

- The promoted artifact table contained `0` rows.
- No protected dependencies or retention holds existed.
- The current workload evidence did not establish repeated semantic replay
  identities that would justify writing and protecting artifacts on the read path.
- The existing semantic-identity lookup is indexed, but an empty lookup cannot
  demonstrate a production benefit over the already effective checkpoint path.
- Promotion would add lifecycle, dependency, invalidation, rebuild, and retention
  complexity without a measured reuse population.

Promoted artifacts were not present in the production-like measurement dataset.
The read path is validated against a real PostgreSQL schema with a promoted artifact,
but the measured dataset still lacks a representative artifact reuse population.

## Implementation Outcome

- Added artifact-first lookup to `OrderBookReplayService` with canonical fallback.
- Added optional `semanticIdentity` to the internal replay endpoint and Feign
  contracts.
- Added the Market Intelligence replay identity caller for context acquisition and
  readiness checks. The identity is deterministic for market, depth, and cutoff.
- Excluded invalidated sequences from artifact lookup.
- Added tests for matching artifact reads and canonical fallback.
- Added PostgreSQL Testcontainers coverage for artifact reads, corruption fallback,
  invalidation, supersession, and retention lifecycle behavior.
- Added canonical payload reconstruction during promotion and HMAC-SHA256 artifact
  fingerprints for fail-closed read-time integrity validation.
- Promotion now locks and revalidates sequence eligibility, and automatically creates
  a protected source dependency for each promoted artifact. Artifact reads require
  that dependency to remain protected until explicitly released.
- Added a PostgreSQL sequence lock held by the artifact-first transaction so
  invalidation cannot commit between eligibility validation and response creation.
- Added full recursive replay-result equivalence assertions and a 10001-row latency
  comparison: canonical `639395 us`, artifact-first `5833 us`, or approximately
  `109.61x` faster in the Testcontainers run.
- Added the required `ORDER_BOOK_ARTIFACT_SIGNING_KEY` production configuration.
- Fixed PostgreSQL timestamp binding in the retention purge query; H2 did not expose
  the unsupported `Instant` parameter binding.
- No migration or index change.
- No replay artifact was created in the measured database.
- No evidence, dependency, or retention state was modified.
- ADR-069 was accepted by the human engineer on 2026-10-21.
- Existing ADR-063 and ADR-067 boundaries remain sufficient for the current state.

## Validation

- Targeted replay, persistence, and retention tests: passed.
- PostgreSQL-backed `OrderBookRetentionPersistenceTest`: passed, 14 tests.
- Full `market-data` Maven test suite: passed after the PostgreSQL Testcontainers
  test change.
- New targeted artifact-first, canonical-equivalence, and persistence tests: passed.
- PostgreSQL artifact benchmark: passed with a measured artifact-first speedup on
  10001 evidence rows; the latest run measured `639395 us` canonical versus `5833 us`
  artifact-first (`109.61x`).
- PostgreSQL concurrent invalidation test: passed; the real artifact-first replay
  path and invalidation operation were executed concurrently, with the result
  asserted according to commit/completion ordering.
- PostgreSQL checkpoint inventory and canonical-versus-checkpoint query plans:
  passed.
- `git diff --check`: passed.

Mockito, H2, Hibernate, SpringDoc, and generated development-security warnings
were emitted by the test environment. No test failure was reported.

## Documentation Reconciliation

Documentation update: required and completed. This report records the measured
checkpoint benefit, artifact rejection, lifecycle implications, and residual gap.
The internal replay contract now accepts an optional semantic identity. Market
Intelligence supplies the deterministic identity; other existing callers preserve
canonical behavior by omitting it.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: repository source, accepted ADRs, local PostgreSQL measurements, and
  Stories 0141/0142 were sufficient.

## Remaining Gap

The benchmark is representative of the bounded test dataset, not the full 49.75 M
row production-like inventory. The representative cold/warm/repeated production-like
artifact comparison remains open. A fresh independent review is still required before
the Story can be marked complete.

## Review Status

Implementation and documentation reconciliation are complete. The independent review
findings were addressed by canonical promotion validation, HMAC integrity, transactional
sequence locking, protected artifact dependencies, full-field equivalence assertions,
and a PostgreSQL artifact benchmark. Final targeted and full Market Data validation
passed. Human approval remains pending. No push or merge was performed.
