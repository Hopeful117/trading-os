# S1 Replay and Fee Evidence Zero-Coverage Root Cause Investigation

**Investigation timestamp:** 2026-10-21T04:37Z (runtime host time)
**Scope:** readiness predicates, live observations, Market Data replay, and historical fee lookup
**Mode:** read-only runtime and repository investigation

## Executive Findings

The premise is no longer true for the current runtime. The authoritative internal
readiness endpoint returned:

| Metric | Current value |
|---|---:|
| S1 signals | 40 |
| Replay | 0/80 (0.0%) |
| Fees | 67/80 (83.75%) |
| Gate | false |

The earlier all-zero fee state was reproduced in logs at 04:28 and 04:29: the
first fee request received HTTP 401. `ResearchReadinessService` catches that
runtime failure, marks the shared fee budget unavailable, and returns zero for
all subsequent fee checks in that evaluation. A later evaluation reached the
historical data and returned 67/80.

Replay remains zero because the first trusted replay request times out while
Market Data executes the full-table `findTrusted` query. The readiness service
then marks replay unavailable and returns zero for all remaining checks. This is
an operational replay defect, not evidence that every post-recovery cutoff lacks
an eligible sequence.

## Repository Baseline

| Item | Result |
|---|---|
| Branch | `main` |
| HEAD | `e9034a97` (`Merge pull request #154`, fee coverage fix) |
| Working tree | Pre-existing unrelated untracked reports under `docs/`; this report is the only new file from this investigation |
| Relevant retention commits | `99fee1b` bounded retention; `7691c01` retention controls; merged at `e9034a97` |
| Relevant fee commit | `06960ed` preserve historical fee coverage after gaps |
| Relevant ADRs | `ADR-063` strict Order Book replay; `ADR-067` bounded retention and replay promotion |
| Relevant Stories | `0111`, `0112A`, `0115`, `0133`, `0138` |
| DevLog | Checked; broad request timed out and narrowed request failed for provider quota. Repository/runtime evidence is authoritative for current state. |

The runtime was recovered from the post-fix deployment at approximately
2026-10-20T20:16:03Z. `market-data` has continued to capture trusted evidence;
the active sequence is `a683821e-c5a3-4326-bbad-5f9bf54517bc`.

## Readiness Population

`ResearchReadinessService.computeCurrent()` selects feature snapshots for the
configured market, strict observed policy, and all time. S1 then retains only
snapshots whose `trend.trigger.break_status` is exactly
`CONFIRMED_BULLISH_BREAK` or `CONFIRMED_BEARISH_BREAK`. Each signal contributes
two checks, at `evaluationCutoff` and `evaluationCutoff + 1h`.

Therefore:

```text
Replay denominator = signal count * 2 = 40 * 2 = 80
Fee denominator    = signal count * 2 = 40 * 2 = 80
```

The denominators are the same population. `approximateAvailableResearchPopulation`
is a different outcome population and is not the S1 evidence denominator.

Current endpoint snapshot:

```text
feature snapshots: 106
earliest cutoff:   2026-10-16T22:00:00Z
latest cutoff:     2026-10-21T04:00:00Z
signals:           40 (18 LONG, 22 SHORT)
replay:            0/80, 0.0%
fees:              67/80, 83.75%
reasons:           signal count, direction counts, period, replay, fee coverage
```

## Replay Predicate Chain

The actual evaluation order is:

1. A signal must exist in the S1 population.
2. The replay client must be present.
3. The shared remote-check budget must have capacity and replay must not already be marked unavailable.
4. Market Intelligence calls `GET /internal/v1/order-book/replay/{marketId}` with depth `25` and the selected cutoff.
5. The response status must equal `AVAILABLE`; any runtime exception returns zero and marks replay unavailable for the rest of the evaluation.
6. Market Data validates non-null market, positive depth, and non-null cutoff.
7. Market Data selects trusted evidence with `known_at <= cutoff` and applies its eligible-sequence rule: active sequences qualify when they have an eligible trusted snapshot; sealed sequences additionally require trusted evidence with `known_at >= cutoff`.
8. Exactly one eligible sequence must remain. Otherwise the query returns no selected sequence.
9. A trusted `SNAPSHOT` must exist in the returned evidence. There is no separate checkpoint lookup in the current implementation.
10. The latest trusted snapshot is selected, then same-sequence evidence after it is ordered by local ordinal.
11. Every item after the snapshot must be a `DELTA` and contiguous by local ordinal.
12. The reconstructed result is returned as `AVAILABLE`.

For the live zero result, the first failing predicate is step 4: the remote
replay request does not complete before the configured read timeout. Steps 5-12
are not reached by readiness for that evaluation.

## Replay Sample Trace

All sampled rows use market `e23ba62a-995f-4755-8deb-11a71ab3788b`, provider
`KRAKEN`, product `LINEAR_PERPETUAL`, canonical symbol `BTC/USD`, provider symbol
`PF_XBTUSD`, and depth 25. The FeatureSnapshot ID is the observation identity;
there is no separate signal ID in this implementation.

| FeatureSnapshot | Signal UTC | Direction | T0/T1 replay cutoffs | Expected sequence from stored bounds | Result |
|---|---|---|---|---|---|
| `589fe2a7-d475-494e-8fb4-c9ea01721683` | 2026-10-17 00:00 | SHORT | 00:00, 01:00 | none: pre-recovery | pre-recovery no evidence |
| `a00d5a4f-ee69-41b8-a64d-40f8b76bf353` | 2026-10-17 17:00 | SHORT | 17:00, 18:00 | none: pre-recovery | pre-recovery no evidence |
| `dee0ce88-e281-4e91-97bb-8216f82fbd3d` | 2026-10-17 20:00 | LONG | 20:00, 21:00 | none: pre-recovery | pre-recovery no evidence |
| `a95d2898-2184-4d24-bdb2-58105242db93` | 2026-10-17 22:00 | SHORT | 22:00, 23:00 | none: pre-recovery | pre-recovery no evidence |
| `4a76d9f1-f973-41f0-9042-d86269944748` | 2026-10-20 21:00 | LONG | 21:00, 22:00 | `362a79cd` for both | post-recovery evidence present; call timed out |
| `36f9ebfb-6116-4469-9fc4-442d0b8d2f0e` | 2026-10-20 22:00 | SHORT | 22:00, 23:00 | `362a79cd` for both | post-recovery evidence present; call timed out |
| `eea7c98c-7b25-474f-9094-2a26abdd7ef8` | 2026-10-20 23:00 | SHORT | 23:00, 00:00 | `362a79cd`, then `b3dcd865` | post-recovery evidence present; call timed out |
| `ca92ce0c-a1fa-4741-b189-6a518895b56f` | 2026-10-21 00:00 | LONG | 00:00, 01:00 | `b3dcd865` for both | post-recovery evidence present; call timed out |
| `ef41215d-ee69-4547-a8d9-eb5372a6cc8b` | 2026-10-21 02:00 | SHORT | 02:00, 03:00 | `28c20f10`, then `a9c95c37` | post-recovery evidence present; call timed out |
| `9e840a08-8a0f-443b-9517-6b8b592c5530` | 2026-10-21 04:00 | SHORT | 04:00, 05:00 | active `a683821e` | post-recovery evidence present; call timed out |

The sequence bounds used above were queried from the authoritative evidence
database. For example, `362a79cd` has trusted evidence from 22:59:48 through
23:43:35, `b3dcd865` from 23:58:22 through 00:32:38, `28c20f10` from 01:53:49
through 02:23:23, and `a9c95c37` from 02:53:26 through 03:24:22. The active
sequence began at 03:24:23. The direct replay call for a post-recovery cutoff
also timed out after 30 seconds with no response.

### Replay failure distribution

```text
80/80 readiness checks: REPLAY_QUERY_TIMEOUT / remote replay exception
66/80 checks: cutoff predates healthy post-fix capture
14/80 checks: post-recovery cutoff with physically present trusted sequence evidence
```

The 66/80 pre-recovery count is a historical population limitation, not a
runtime acquisition failure. The post-recovery 14/80 count proves that waiting
does not make the current replay request operationally usable.

Retention did not remove the sampled evidence. The database contains active and
sealed sequences, and no retirement operation was observed. The failure is not
`REQUIRED_EVIDENCE_RETIRED`.

```text
MAX_REPLAY_COVERAGE for the current population and currently collected
post-recovery evidence = 14/80 = 17.5%
```

This is a conservative evidence-availability maximum for the frozen current
population. It is below the 90% gate because 66 checks require pre-recovery
history that cannot be reconstructed from evidence collected later. New future
signals can be covered, but they do not repair those historical rows.

Should eight hours of healthy collection have produced replay coverage? **YES**
for the 14 post-recovery checks, if the replay endpoint were operational. **NO**
for the 66 pre-recovery checks. The current result is therefore not natural
accumulation alone.

**Primary replay classification:** `REPLAY_RECONSTRUCTION_DEFECT` is not yet
appropriate because reconstruction is never reached. The precise classification
is `REPLAY_OTHER_DEFECT`: bounded trusted replay is operationally unusable due
to the full-table query/read-timeout path.

**Replay remediation classification:** `BOUNDED_CODE_FIX_REQUIRED`.

Minimal remediation is to make the trusted replay query bounded and index-usable
against the existing evidence population, then replay the same post-recovery
cutoffs. Do not lower coverage, alter cutoffs, accept invalid sequences, or
delete the historical population. Separately, the frozen population’s 90% gate
reachability requires an explicit lifecycle/architecture decision; it must not
be silently reset or rebuilt.

## Fee Predicate Chain

The actual evaluation order is:

1. A signal must exist in the same S1 population.
2. The historical fee client and broker account ID must be non-null. Configuration validation requires all four fee identity fields when any field is configured.
3. The client requests `(brokerAccountId, provider, productType, instrument, executionTime, researchCutoff)`.
4. Broker Service requires exact equality for account, provider, product type, and instrument.
5. The schedule must have `effectiveFrom <= executionTime` and `effectiveTo` null or after execution time.
6. The schedule must have `knownAt <= researchCutoff` and `origin = CAPTURED`.
7. A 2xx response with a non-null body is covered. HTTP 404 is an uncovered historical gap. Any other runtime exception marks the shared fee path unavailable and makes later checks zero for that readiness evaluation.

Current effective configuration is:

| Key | State | Semantic value |
|---|---|---|
| `RESEARCH_LIFECYCLE_FEE_COVERAGE_BROKER_ACCOUNT_ID` | CONFIGURED | `0c21f957-3d86-413e-a2a8-42d67e93c7d5` |
| `RESEARCH_LIFECYCLE_FEE_COVERAGE_PROVIDER` | CONFIGURED | `KRAKEN` |
| `RESEARCH_LIFECYCLE_FEE_COVERAGE_PRODUCT_TYPE` | CONFIGURED | `LINEAR_PERPETUAL` |
| `RESEARCH_LIFECYCLE_FEE_COVERAGE_INSTRUMENT` | CONFIGURED | `PF_XBTUSD` |

The authoritative broker database contains one captured open schedule with the
same identity:

```text
scheduleId:    78156abe-819d-4cf1-9d98-f890a967d4d7
effectiveFrom: 2026-10-17T22:27:35.208282Z
effectiveTo:   null
knownAt:       2026-10-17T22:27:35.208332Z
origin:        CAPTURED
maker/taker:   0.0002 / 0.0005
```

The fee timestamp is not signal creation time. It is exactly
`FeatureSnapshot.evaluationCutoff` and that cutoff plus one hour. The schedule
therefore covers all 67 checks at or after its effective time and cannot cover
the 13 earlier checks.

### Fee trace and distribution

The first failing predicate for the 13 current uncovered checks is:

```text
FIRST_FEE_FAILURE = NO_HISTORICAL_SCHEDULE / TIMESTAMP_NOT_COVERED
```

They are the checks at 2026-10-17 00:00, 01:00, 02:00, 17:00, 18:00,
19:00, 20:00, 21:00, and 22:00 where applicable. The 22:00 signal’s 23:00
check is covered. The other 67 checks resolve the authoritative schedule.

The historical all-zero state has a separate first failure:

```text
FIRST_FEE_FAILURE during the 04:28 evaluation = BROKER_SERVICE_UNAUTHORIZED
```

The log records HTTP 401 for the first request at execution time
2026-10-17T00:00Z. The implementation’s broad runtime catch then sets
`feeUnavailable`, so all remaining checks return zero without lookup. This is
why a transient/authentication lookup failure can present as global 0%, and why
the current re-evaluation is 67/80 after the path became reachable.

```text
MAX_FEE_COVERAGE with current configuration and captured history = 67/80 = 83.75%
```

Waiting longer cannot increase coverage for the 13 historical timestamps unless
the broker service ingests a schedule whose effective interval covers them. It
does not need another eight-hour runtime window for the existing 67 checks.

Should eight hours have changed fee coverage? **NO** while the first lookup was
returning 401 and the shared fail-closed flag was set. **YES** after service
authentication was corrected, and it did: the current value is 67/80.

**Primary fee classification:** `FEE_LOOKUP_DEFECT` for the historical zero
state, specifically the service-authentication failure combined with global
short-circuiting. The current residual 13 checks are a legitimate
`FEE_TIMESTAMP_COVERAGE_GAP`, not a configuration mismatch.

**Fee remediation classification:** `BOUNDED_CODE_FIX_REQUIRED` for the global
error-classification/diagnostic behavior, plus `ARCHITECTURE_DECISION_REQUIRED`
if the project requires historical schedules before 2026-10-17T22:27Z. Do not
use current fees, zero fees, or guessed fees.

## Diagnostics, Logs, and Tests

The readiness endpoint reports only aggregate numerator/denominator and blocking
reason `trusted_replay_coverage_below_90_percent` or `fee_coverage_incomplete`.
It cannot distinguish timeout, missing sequence, invalid sequence, 401, 404, or
timestamp coverage. The endpoint is therefore insufficient to explain the
original zero without logs and direct evidence tracing.

Relevant runtime evidence:

- `market-intelligence` logged two HTTP 401 historical-fee failures at 04:28 and 04:29.
- A direct authenticated fee lookup for 2026-10-20T21:00Z returned the captured schedule.
- A direct authenticated replay lookup for 2026-10-20T21:00Z timed out after 30 seconds.
- `market-data` has one active and many sealed sequences; post-recovery trusted evidence is present and retained.
- No retirement operation or evidence deletion was observed.

Relevant tests include `ResearchReadinessServiceTest`, especially the tests for
missing fee identity, T0/T1 replay and fees, 404 fee gaps, remote budget limits,
and dependency failure. Existing tests do not prove the live 36M-row replay
query completes, do not prove a post-recovery runtime observation, and do not
assert that a broker 401 is reported distinctly from a historical 404.

Validation executed: `mvn -q -f market-intelligence/pom.xml
-Dtest=ResearchReadinessServiceTest test` passed. `git diff --check` passed for
the report. No runtime restart, configuration change, database mutation,
evidence deletion, or timestamp manipulation was performed.

## Classification and Next Actions

| Area | Classification | Minimal remediation | Architecture decision? |
|---|---|---|---|
| Replay | `REPLAY_OTHER_DEFECT` | Fix bounded/index-usable replay query and verify the same cutoffs | Yes, for frozen-population reachability/reset semantics; not for the query fix |
| Fees | `FEE_LOOKUP_DEFECT` for historical zero; residual `FEE_TIMESTAMP_COVERAGE_GAP` | Fix service-auth/error classification and decide whether to ingest pre-schedule history | Yes, only if pre-schedule history is required |

After replay remediation, the same 14 post-recovery checks should become
available immediately if their sequence selection and reconstruction complete;
the 66 pre-recovery checks should remain unavailable. After fee lookup
authentication is healthy, the current 67 checks are already immediately
available; only the 13 pre-schedule checks require new historical evidence.

Validation plan:

```text
apply bounded replay/auth fix
-> invoke readiness with the same authenticated path
-> call replay for the sampled post-recovery cutoffs
-> verify sequence, trusted starting snapshot, contiguous deltas, and AVAILABLE
-> verify fee 67/80 remains available and 401 is no longer converted to global zero
-> report residual historical gaps without weakening fail-closed behavior
```

`PRODUCT_DEVELOPMENT_GATE = HOLD` pending the bounded replay fix and an explicit
decision for the frozen historical population. The appropriate final state is
`PRODUCT_DEVELOPMENT_CAN_RESUME_AFTER_SMALL_FIX` once replay is operational and
the fee authentication/error path is corrected; it is not authorization to
implement Post-trade Decision History.

## Mutations and Artifact

- Report artifact: `docs/architecture/reports/s1-replay-fee-zero-coverage-root-cause-investigation.md`
- Repository source/configuration changes: none
- Runtime mutations: none; read-only endpoint, logs, and database queries only
- Database mutations: none
- Configuration changes: none
- Collection/replay side effects: no restart and no evidence deletion; direct replay requests timed out and were not mutating operations

**Exact next action:** fix the bounded Market Data trusted-replay query path,
then re-run the same 14 post-recovery replay cutoffs and preserve the 66
pre-recovery failures as explicit historical-population evidence. Separately
fix or diagnose the Broker Service service-JWT path so one 401 cannot collapse
all fee diagnostics to zero.

## Final Summary

```text
REPLAY ROOT CAUSE:
The readiness replay client times out on Market Data's unbounded trusted-evidence query, and the service then fail-closes every remaining replay check; 66 of 80 checks are also permanently pre-recovery population rows.

FIRST FAILING REPLAY PREDICATE:
REPLAY_REMOTE_CALL_TIMEOUT

MAXIMUM CURRENT REPLAY COVERAGE:
17.5%

FEE ROOT CAUSE:
The historical 0% state was caused by a Broker Service HTTP 401 on the first lookup plus the shared fee-unavailable short-circuit; the current state is 67/80 because configuration and one captured schedule now resolve.

FIRST FAILING FEE PREDICATE:
BROKER_SERVICE_UNAUTHORIZED (historical zero); NO_HISTORICAL_SCHEDULE / TIMESTAMP_NOT_COVERED (current residual failures)

MAXIMUM CURRENT FEE COVERAGE:
83.75%

WAITING LONGER WITHOUT A CHANGE WILL:
NOT HELP

NEXT ACTION:
Fix and directly validate the bounded trusted replay query, then fix the Broker Service service-JWT path and repeat readiness against the same observations.
```
