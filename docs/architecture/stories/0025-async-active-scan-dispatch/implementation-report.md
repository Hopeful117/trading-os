# Implementation Report — Story 0025

## Change set (backend only — Market Intelligence)

- `IntelligenceExecutionConfiguration`: new managed bean
  `scanDispatchExecutor` (`newVirtualThreadPerTaskExecutor`, `shutdown` on
  destroy) — sibling of the existing analysis executor, same house convention.
- `ActiveScanDispatchCoordinator`: constructor-injected executor + new
  `resumeAsync(scanId)`; the submitted task wraps `resume()` with start /
  complete / failure logging (scanId always present). `resume()` body and the
  sequential per-market claim+dispatch loop are byte-for-byte unchanged.
- `ActiveScanApplicationService.registerAfterCommitIfNeeded`: afterCommit now
  calls `resumeAsync`. Synchronization still registered only for non-terminal
  scans, still fires strictly after commit.
- **Zero Angular changes. Zero nginx changes.**

## Runtime before

```
POST /scans ≈161 s on request thread → nginx 504 at 60 s → Angular UNAVAILABLE
no scanId delivered → no polling → aggregate frozen in DISPATCH_REQUESTED
```

## Runtime after (real Kraken scope, 1 436 eligible markets)

| Measure | Before | After |
|---|---|---|
| POST duration | ≈161 s (never returned) | **3.6 s** (scope resolution + persist 1 436 children) |
| HTTP result | 504 (nginx HTML) | **202** + Location + `{scanId, READY_TO_DISPATCH}` |
| scanId delivered | never | yes |
| GET polls | none possible | every ~2 s, all 200 |
| Terminal reached | never | **COMPLETED** (done=1379/1379, fail=0, opportunitiesFound=1329) |
| Opportunities in UI list | n/a | 3 429 rows rendered during run |

## Lifecycle observation after async fix

First async scan tracked by polling:

```
READY_TO_DISPATCH (202 body)
  → RUNNING            (first GETs; live counts: running↓ completed↑)
  → COMPLETED          (final poll; run=0 done=1379 opp=1329)
```

Persistence proof: `active_scans.status = 'COMPLETED'`,
`updated_at = instant of the last polling GET` — read-time reconciliation not
only derives but *persists* forward progress.

### Reconciliation verdict: SUFFICIENT

The existing read-time reconciliation (`classify()` over
`analysis_executions` + pipeline runs + opportunities) drove the aggregate to a
correct honest terminal state through polling alone. Per Story constraint #28,
**no child write-back was added**. Documented characteristic:
`active_scan_markets.status` stays at its dispatch-time value;
`updated_at` on the aggregate advances only when someone reads the projection.
If this design should be explicit rather than emergent, a separate
documentation clarification is suggested (not implemented here).

## Threading proof

- Request thread: returns inside `create()` right after commit registers the
  synchronization; `afterCommit()` merely submits to `scanDispatchExecutor`
  and returns (proven by `ActiveScanDispatchAsyncBoundaryTest`: worker observed
  blocked mid-claim on a virtual thread while caller already proceeded).
- Dispatch worker thread: virtual thread named/owned by the dedicated
  executor; runs the unchanged sequential loop (~7 min for full Kraken scope).
- Analysis threads: unchanged virtual-thread-per-task dispatcher.

## Failure & shutdown semantics (documented, no speculative infra)

- Worker exception → logged with scanId; aggregate remains pre-terminal.
- Recovery path that exists today: client retry with the same
  `Idempotency-Key` hits the idempotent replay branch which re-registers
  dispatch after commit.
- Crash after 202 with no retry ⇒ scan stays non-terminal until replayed or
  read-reconciled; undispatched children keep derived status RUNNING forever.
  Architectural characteristic of the V1 local-dispatcher design, accepted for
  this story, surfaced for a future durability story.

## Quality gates

| Gate | Result |
|---|---|
| MI tests (`mvnw test`) | 294 pass (293 existing + 1 new boundary test) |
| Angular tests | 36 files / 216 pass (unchanged code) |
| Angular build | 0 errors |
| `git diff --check` | clean |
| Docker build market-intelligence | OK, stack restarted, app started 7.3 s |

## Idempotency & claiming

Unchanged paths exercised at runtime (fresh key per run; duplicate-key branch
covered by existing tests). Claim service still gates every child dispatch.
