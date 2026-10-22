# Repository Analysis: Story 0144

## Status

Repository Analysis prepared for human approval. No retention mutation or
application code change was performed as part of this analysis.

## Authority and Baseline

- Story: `0144`, approved.
- Related Stories: `0143`.
- Related ADRs: `ADR-067`, `ADR-068`, `ADR-069`.
- Branch: `main`.
- HEAD and `origin/main`: `907a614672477d8518e9b0ae51be129146a90a29`.
- Worktree: pre-existing untracked investigation reports and the Story 0144
  directory; no tracked-file modifications were introduced.
- DevLog: unavailable during this investigation because the provider returned
  HTTP 429 `insufficient_quota`. Repository and approved Story artifacts are
  therefore the available authorities.

## Scope Interpretation

This Story is an operational validation gate. It does not authorize changes to
S1 semantics, readiness thresholds, replay trust rules, retention rules,
provider scope, or the V1 product path. The first safe action after approval is
the supported retention preview. A real retirement is permitted only after the
preview and representative trusted replay establish that a small candidate is
safe.

The requested T+2h/T+4h/T+8h window cannot be claimed from the current session.
If the execution environment cannot remain active for the full window, the
validation report must use `PENDING_RUNTIME_WINDOW` and include exact follow-up
commands and thresholds.

## Current Runtime Baseline

The Docker environment is running with `market-data` exposed on host port
`17083`; its PostgreSQL container is `market_data-postgres`.

Read-only PostgreSQL counts at analysis time:

| Relation | Rows |
| --- | ---: |
| `historical_order_book_sequence` | 226 |
| `historical_order_book_evidence` | 61,524,201 |
| `historical_order_book_replay_artifact` | 1 |
| `historical_order_book_evidence_dependency` | 1 |
| `historical_order_book_retention_hold` | 0 |
| `historical_order_book_retirement_operation` | 1 |

Sequence statuses:

| Status | Count |
| --- | ---: |
| `ACTIVE` | 1 |
| `SEALED` | 149 |
| `INVALIDATED` | 76 |

The only recorded retirement operation is an incomplete `DRY_RUN` operation in
`SELECTED` state with zero affected rows and no completion timestamp. This
confirms the Story's operational premise but does not prove that the current
preview is unsafe.

A broad diagnostic query joining all evidence to all sequences exceeded the
two-minute command limit. It was not repeated because the evidence table is
large; subsequent runtime queries must use indexed, bounded predicates and
small result sets.

## Implementation Findings

### Retention lifecycle

`OrderBookRetentionService.previewRetirement()`:

- selects candidates through `OrderBookRetentionRepository`;
- records a deterministic dry-run fingerprint and details;
- returns candidate sequence, ordinal, timing, and evidence-count metadata.

Candidate selection excludes:

- active sequences;
- non-released dependencies;
- active retention holds;
- sequences without a released dependency marker;
- sequences with untrusted replay artifacts.

`retire()` requires destructive retirement to be enabled and requires the
candidate and dry-run fingerprint to match a recorded preview. It performs a
bounded canonical repository deletion, records affected rows, and distinguishes
completed from partial operations. The SQL deletion itself is application-owned
retention behavior, not an ad hoc operator SQL action, and remains prohibited
until the preview and replay checks are approved.

### Replay lifecycle

`OrderBookReplayService`:

- first attempts a matching trusted artifact when a semantic identity is
  supplied;
- checks current sequence eligibility and artifact fingerprint;
- otherwise selects one eligible sequence, a trusted snapshot, and same-sequence
  deltas with `known_at <= cutoff`;
- rejects missing snapshots, unexpected evidence types, and non-contiguous
  ordinals;
- returns provenance, trust status, source ordinals, and evidence fingerprints.

The repository query limits eligible sequence discovery to two candidates and
fails closed unless exactly one sequence is eligible. The replay evidence query
is bounded by market, depth, sequence, anchor ordinal, and cutoff, but it has no
explicit row limit. Runtime replay must therefore use a representative small
post-recovery interval and record latency/evidence volume rather than attempting
an unrestricted historical replay.

### Storage and acquisition health

`OrderBookStorageHealthService` exposes usable bytes, minimum free bytes,
pressure state, persistence compromise, last successful persistence, active
sequence diagnostics, and an aggregate `HEALTHY`/`DEGRADED` capture state.
Capture is unavailable when storage pressure or durability compromise is true.

### Security and endpoints

Canonical internal endpoints are:

- `GET /internal/v1/order-book/retention/health`
- `GET /internal/v1/order-book/retention/preview`
- `POST /internal/v1/order-book/retention/retire`
- `GET /internal/v1/order-book/replay/{marketId}?depth={depth}&asOf={cutoff}`
- optional replay artifact lookup via `semanticIdentity`.

The internal endpoints are protected by service JWT authorization. Runtime
validation must use an authorized service identity and must not expose or print
credentials.

## Tests and Existing Evidence

Relevant tests already cover:

- cutoff filtering and trusted snapshot requirements;
- contiguous replay and unexpected evidence rejection;
- trusted artifact lookup and canonical fallback;
- untrusted/incomplete artifact fallback;
- retention persistence and lifecycle behavior;
- endpoint authorization for replay, preview, dependency, and artifact paths.

No code defect requiring an implementation change was established during this
analysis. The remaining uncertainty is runtime evidence, not a confirmed
repository defect.

## Approved Runtime Validation Sequence

After human approval of this analysis:

1. Capture bounded T0 health, sequence-status, artifact/dependency/hold counts,
   FeatureSnapshot and ObservedMarketOutcome integrity counts, table/index sizes,
   and recent application error summaries.
2. Execute `GET /internal/v1/order-book/retention/health` and the supported
   retention preview endpoint. Preserve the returned fingerprint and candidate
   details without modifying evidence.
3. Verify every selected candidate is non-active, dependency-safe,
   hold-free, artifact-safe, replay-safe, and small enough for one reviewed
   retirement batch.
4. Select a representative valid post-recovery cutoff and execute canonical
   trusted replay before retirement. Record status, sequence, ordinals,
   `knownAt`, latency, and bounded evidence volume.
5. If and only if steps 2-4 are safe, execute one small canonical retirement
   request using the exact preview fingerprint and candidate returned by the
   endpoint.
6. Re-read operation state, affected/protected evidence, active-sequence
   continuity, FeatureSnapshot and ObservedMarketOutcome integrity, and storage
   metrics. Re-run representative replay after retirement.
7. Re-evaluate the same operation through the supported lifecycle to verify
   idempotency without raw destructive SQL.
8. Capture T+2h, T+4h, and T+8h checkpoints if the environment remains active;
   otherwise mark `PENDING_RUNTIME_WINDOW` and preserve exact read-only
   commands and acceptance thresholds in the validation report.

## Decision Gate

**Repository Analysis approval required.** No implementation plan or real
retirement operation should proceed until this analysis is approved. If runtime
validation reveals that safe retention requires new domain semantics or an
architectural change, stop with `ARCHITECTURE_DECISION_REQUIRED` rather than
modifying the retention implementation inside this Story.
