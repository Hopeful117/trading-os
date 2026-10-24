# S1 Overnight Operational Health

**Investigation date:** 2026-10-22 05:50-06:03 UTC bounded observation
**Investigation mode:** read-only repository and runtime investigation
**Implementation performed:** none
**DevLog:** applicability check attempted; Story Agent timed out. Repository and runtime evidence are authoritative for current state.

## 1. Repository Baseline

| Item | Result |
|---|---|
| Branch | `story/0143-operational-replay-artifact-population` |
| HEAD | `a707bb8e4c2bb121d061802917a3443a55c862a4` |
| `origin/main` | `907a614672477d8518e9b0ae51be129146a90a29` |
| Worktree | clean before this report |
| Recent merged/current work | Stories 0139-0143; replay performance, retention, readiness, and artifact population |
| Relevant ADRs | ADR-066 snapshot/outcome identity; ADR-067 bounded retention; ADR-068 readiness projection; ADR-069 replay acceleration |
| Relevant Story | 0143, implementation complete, runtime validation pending |
| Previous runtime evidence | 2026-10-20 retention/replay validation reported unbounded growth and replay timeout |

The current branch contains the post-recovery replay indexes and the operational
artifact population implementation. The previous runtime report is historical
context, not a substitute for the current checkpoint.

## 2. Runtime Baseline

At T0, all Trading OS containers were running with zero observed Docker restart
counts for the relevant services. Trading Core's actuator health returned `UP`.
Market Data and Market Intelligence actuator calls were rejected without their
authenticated/internal authorization, which is an expected security boundary,
not a health failure.

| Resource | T0 observation |
|---|---:|
| Host filesystem | 449G total, 362G used, 65G free, 85% used |
| Docker footprint | 8.8G images, 41.2G volumes, 10.3G build cache |
| Market Data DB | accepting read queries |
| Market Intelligence DB | accepting read queries |
| Relevant service restarts | 0 since current container starts |

No service was restarted and no database or research row was modified by this
investigation.

## 3. Configured Market Scope

The active runtime evidence and research tables show one configured S1 market:

| Identity | Value |
|---|---|
| Market | `e23ba62a-995f-4755-8deb-11a71ab3788b` |
| Provider stream | Kraken Futures, `PF_XBTUSD`, depth 25 |
| Research market representation | the same market ID in all 134/135 snapshots |
| Outcome horizons | 1h, 4h, 24h |

The Compose contract exposes the same Order Book provider symbol and depth for
acquisition. No incompatible or unintended market was found in the bounded
database comparison. The runtime does not expose the configured environment
values without inspecting secrets, so this conclusion is based on persisted
market/sequence identity and current acquisition logs.

## 4. Actual Pipeline

```text
Kraken Futures WebSocket
  -> normalized Order Book sequence/snapshot/delta persistence in Market Data
  -> aligned cutoff in Market Intelligence
  -> STRICT_OBSERVED passive AnalysisExecution
  -> FeatureSnapshot using feature-projection-v1
  -> frozen trigger classifier and canonical Trend Context artifact
  -> ObservedMarketOutcome at 1h/4h/24h maturity
  -> bounded trusted replay and historical fee checks for readiness
  -> readiness projection
```

| Stage | Owner | Trigger/persistence | Failure and retry behavior |
|---|---|---|---|
| Provider acquisition | Market Data | Kraken WebSocket; canonical evidence PostgreSQL | Stream termination starts a new sequence; persistence is fail-closed |
| Cutoff submission | Market Intelligence | fixed-delay scheduler; idempotency key `market + aligned cutoff` | duplicate cutoff is skipped; submission failure is logged and retried on later scheduler pass |
| Feature projection | Market Intelligence | completed passive execution -> immutable `feature_snapshots` | projection failure remains visible in logs; no replacement snapshot is fabricated |
| Signal/regime classification | Market Intelligence | readiness reads frozen snapshot feature and Trend Context artifact | missing trigger/regime fails closed for readiness |
| Outcome maturation | Market Intelligence | each matured snapshot/horizon; immutable outcome row | evaluation failure is logged; status remains explicit rather than invented |
| Replay | Market Data | authenticated bounded replay contract; artifact-first where reusable | invalid/missing artifact falls back to canonical replay or unavailable |
| Fees | Broker Service/Market Intelligence | configured historical schedule lookup | 404 means unavailable historical evidence; other failures remain unavailable and visible |
| Retention | Market Data | preview/retire lifecycle endpoints and dependency protection | unknown dependency is protected; no implicit destructive cleanup |

Replay artifacts are not required to create FeatureSnapshots or mature outcomes.
They accelerate readiness and enable safe evidence retirement. Therefore S1
collection itself is independent of artifact reuse, while readiness and future
retention are not.

## 5. Checkpoints

The requested multi-day checkpoints cannot be completed in this interactive
session. The evidence below is explicitly limited to T0 and a roughly five-minute
checkpoint, supplemented by the prior 2026-10-20 report where useful.

### T0 - 2026-10-22 05:57:41 UTC

| Metric | Value |
|---|---:|
| Order Book evidence rows | 54,888,936 |
| Evidence oldest occurred-at | 2026-10-17 12:26:58 UTC |
| Evidence newest occurred-at | 2026-10-22 05:46:07 UTC |
| Evidence newest known-at | 2026-10-22 05:57:41 UTC |
| Sequence states | 1 ACTIVE, 76 INVALIDATED, 124 SEALED |
| Replay artifacts | 1 |
| Retention operations | 1, `DRY_RUN/SELECTED` |
| Protected dependencies | 1 |
| Retention holds | 0 |
| FeatureSnapshots | 134 |
| Newest snapshot cutoff | 2026-10-22 05:00 UTC |
| Outcomes | 991 |
| Outcome status | 666 AVAILABLE, 324 INCOMPLETE, 1 UNAVAILABLE |
| Readiness signal count | 58, from current Market Intelligence log |

### T+approximately 5 minutes - 2026-10-22 06:02-06:03 UTC

| Metric | Value | Delta |
|---|---:|---:|
| Order Book evidence rows | 54,925,604 | +36,668 |
| Evidence newest known-at | 06:02:16 UTC | advanced |
| FeatureSnapshots | 135 | +1 |
| Newest snapshot cutoff | 06:00 UTC | advanced one cadence |
| 1h outcomes | 352 total | +1 incomplete |
| 4h outcomes | 343 total | +1 incomplete |
| 24h outcomes | 298 total | +1 incomplete |
| Retention operations | 1 | no new completed operation |
| Host free space | 65G | no measurable change at displayed precision |

The five-minute checkpoint demonstrates active acquisition, an aligned hourly
research cutoff, and outcome progression. It does not establish multi-day
stability.

## 6. Acquisition Progression

Required Order Book evidence is arriving. The current active sequence remains
valid and evidence `known_at` advances to the checkpoint. The runtime logs show
repeated Kraken stream terminations classified as `NETWORK_FAILURE`, followed by
new sequences and accepted snapshots. Recovery is automatic in the observed
window; the termination frequency is an operational residual and the occurred-to-
known lag in some termination records is large enough to monitor.

Ticker, OHLC, and recent-trades acquisition were not independently counted in
this bounded run. They are not required to establish the Order Book S1 path, and
no failure should be inferred for them from their absence in this report.

## 7. Lifecycle, Snapshots, Signals, and Regimes

The scheduler is enabled in the deployed behavior: the code uses a fixed-delay
pass and the database gained a snapshot at the next aligned hourly cutoff. The
cutoff advanced from 05:00 to 06:00 UTC during the observation.

`FeatureSnapshot` progression is healthy at the bounded scale. All inspected
rows use `feature-projection-v1`; the snapshot query found no missing context
fingerprint, snapshot fingerprint, or provenance payload. The frozen
`FeatureProjectionV1` source was not changed.

The current readiness log reports 58 signals. The source classifier maps only
`CONFIRMED_BULLISH_BREAK` to LONG and `CONFIRMED_BEARISH_BREAK` to SHORT, and
reads regime from the canonical Trend Context artifact. No classifier or regime
semantic drift was found in the current branch.

```text
S1_CLASSIFIER_SEMANTICS = UNCHANGED
```

The 58-signal count is a readiness population measure. It is not an economic
result and was not used to judge collection health.

## 8. Outcome Maturation and Backlog

Outcomes exist for all configured horizons and increased as the lifecycle moved
forward. The current distribution is:

| Horizon | Available | Incomplete | Unavailable |
|---|---:|---:|---:|
| 1h | 240 | 112 | 1 |
| 4h | 231 | 112 | 0 |
| 24h | 195 | 103 | 0 |

The incomplete backlog is substantial but not evidence of a frozen lifecycle:
new rows continue to appear and older rows naturally cross maturity boundaries.
The single unavailable 1h outcome must remain visible and should be explained by
its persisted reason during a deeper operational review. No fabricated result
was observed.

## 9. Order Book, Replay, Artifacts, and Retention

The current database has 54.9M evidence rows, one active sequence, sealed and
invalidated historical sequences, one promoted artifact, and one dependency.
This confirms that Story 0143's runtime population path has executed at least
once.

The retention operation is:

```text
mode=DRY_RUN
status=SELECTED
selected_at=2026-10-20 20:51:45 UTC
completed_at=NULL
affected_count=0
```

No completed mutation or compaction was observed. The current code now exposes
preview and retire endpoints, which corrects the earlier missing-path defect, but
the deployed evidence still does not demonstrate a completed safe retirement
cycle. Storage therefore cannot yet be called bounded in runtime.

The artifact is classified as:

```text
ARTIFACT NECESSITY = OPTIONAL_ACCELERATION / REQUIRED_ONLY_FOR_READINESS
```

It is not a collection-health prerequisite. It is relevant to readiness coverage
and to releasing raw evidence under ADR-067.

The replay implementation is bounded by semantic identity, one sequence, one
checkpoint or snapshot boundary, same-sequence deltas, and `knownAt <= cutoff` in
the current design. Current readiness logs show reuse (`replayKeys=85`,
`replayReuses=31` in the latest sampled computation), but the global readiness
coverage and post-recovery success ratio could not be calculated without an
authenticated readiness response. The log records replay calls and latency but
not a complete numerator/denominator result.

```text
POST_RECOVERY_REPLAY_SUCCESS = INCONCLUSIVE (authenticated numerator/denominator unavailable)
```

The earlier 2026-10-20 replay timeout is historical evidence that the fix must be
validated against the current 54.9M-row population; it is not silently treated as
the current result.

## 10. Fee Evidence

The prior runtime report found all four fee coverage parameters unset. The
current Compose contract still defaults them to empty values, and the current
readiness log reports `feeChecks=116` but does not expose available counts in the
bounded unauthenticated inspection. No recurring normal-runtime `401` was
observed in the sampled Market Intelligence log.

Fee gaps remain a research-readiness issue unless the configured fee path is
required for a separate collection invariant. No historical fee schedule was
fabricated.

## 11. Integrity and Readiness Diagnostics

The internal integrity and readiness endpoints correctly enforce authorization;
unauthenticated direct calls returned `401`/`403`. This prevents a trustworthy
full endpoint payload from being collected without using the existing authenticated
operator path. The source contract classifies the following readiness gates as
experimental gates, not collection-health gates:

| Gate | Classification | Meaning |
|---|---|---|
| Signal count >= 60 | EXPERIMENTAL_READINESS_GATE | sample population size |
| LONG >= 30 | EXPERIMENTAL_READINESS_GATE | directional balance |
| SHORT >= 30 | EXPERIMENTAL_READINESS_GATE | directional balance |
| 14-day signal period | EXPERIMENTAL_READINESS_GATE | chronological sample span |
| At least 2 regimes | EXPERIMENTAL_READINESS_GATE | regime diversity |
| Replay coverage >= 90% | EXPERIMENTAL_READINESS_GATE and operational readiness diagnostic | historical evidence reproducibility, not acquisition liveness |
| Fee coverage = 100% | EXPERIMENTAL_READINESS_GATE and operational dependency diagnostic | cost completeness, not raw collection liveness |
| remote evidence budget | COLLECTION_HEALTH_GATE and readiness gate | protects the readiness service from runaway checks |

Collection-health facts are instead acquisition freshness, durable persistence,
cutoff progression, snapshot creation, outcome maturation, storage pressure, and
absence of recurring critical persistence failures.

## 12. Storage Velocity and Capacity Outlook

The five-minute checkpoint added 36,668 evidence rows. Displayed table size was
not collected at both checkpoints, so a reliable current MB/hour rate cannot be
claimed from this run. The 2026-10-20 measured rate was approximately 0.36
GB/hour, or 8.6 GB/day, with roughly 10-11 days to host exhaustion from 92G free.
The current host has only 65G free, which would be roughly 7.5 days at that old
rate, before accounting for PostgreSQL and other Docker growth.

That is a qualified upper-risk projection, not a steady-state forecast: a
completed lifecycle retirement should reduce raw growth, but no completed
retirement has been observed in the current runtime.

```text
STORAGE_OUTLOOK = UNBOUNDED
```

The status means “not proven bounded and currently accumulating without a
completed retirement cycle,” not that physical exhaustion is imminent today.

## 13. Errors, Recovery, and Resource Stability

Observed in the recent bounded logs:

- repeated Kraken WebSocket `NETWORK_FAILURE` terminations;
- automatic new sequence creation and accepted snapshots after each termination;
- repeated `Broken pipe` warnings caused by clients abandoning slow HTTP responses;
- no new database recovery-mode or canonical evidence persistence failure in the sampled current logs;
- no container restart loop or memory/cpu load test performed.

The broken-pipe pattern is evidence that at least one replay/readiness consumer is
too slow for its caller timeout. It is not evidence of corrupted stored evidence,
but it is an operational defect for unattended research diagnostics.

## 14. Collection-Health Gate

```text
S1_COLLECTION_HEALTHY = NO
S1_COLLECTION_HEALTHY_WITH_RESIDUALS = NO
S1_COLLECTION_DEGRADED = YES
S1_COLLECTION_BROKEN = NO
S1_COLLECTION_INCONCLUSIVE = NO
```

The reason is bounded and operational: acquisition and lifecycle progression work,
but multi-day safety is not established because evidence continues to grow while
the only recorded retirement operation remains an incomplete dry run. The current
storage headroom permits short controlled observation, not an unattended approval.

This verdict is independent of the 58-signal count, directional counts, 14-day
span, replay coverage, fee coverage, economic quality, or experimental results.

## 15. Experimental Readiness

```text
S1 EXPERIMENTAL READINESS = NOT_READY
```

The readiness log reports 58 signals, below the source thresholds, while fee and
replay available counts were not safely retrievable through the authenticated
endpoint in this session. This means the experiment is not ready to interpret; it
does not mean collection is invalid.

## 16. S1 Product-Development Gate

```text
S1_ALLOWS_V1_PRODUCT_DEVELOPMENT = YES_WITH_RESIDUALS
```

The operational storage defect should be fixed and revalidated, but it does not
technically prevent independent V1 work such as PAPER lifecycle validation,
decision history, frontend continuity, tests, or read-only product surfaces.
S1 collection health, experimental readiness, experimental results, and product
readiness remain separate states.

## 17. Minimum Gate Before Unattended Collection

Before returning S1 to multi-day unattended operation, verify all of the following
with current runtime evidence:

1. Required acquisition remains fresh and durably persisted.
2. Aligned cutoffs advance without duplicates or frozen scheduler state.
3. FeatureSnapshots continue with frozen projection/provenance semantics.
4. Matured outcomes continue and failures remain bounded and visible.
5. A valid post-recovery trusted replay completes within the operational timeout.
6. The replay artifact population path is observable and idempotent.
7. The retention preview and resumable retirement operation complete safely for an eligible sequence, with active and protected evidence preserved.
8. Fee evidence is either configured and operational or explicitly classified as a non-blocking readiness residual.
9. Storage headroom and last successful persistence are visible, with fail-before-full behavior.
10. No recurring database/persistence failure or uncontrolled resource instability is present.

No signal-count or economic-performance threshold belongs in this minimum
collection-health gate.

## 18. Exact Next Action

Do not change S1 semantics. Run the separately approved operational remediation
for the bounded replay/retention path, execute a dry-run followed by the reviewed
retirement lifecycle against eligible evidence, and repeat runtime checkpoints at
T+2h/T+4h/T+8h. Keep the current artifact dependency and active sequence
protected. Configure fee evidence separately; do not fabricate it.
