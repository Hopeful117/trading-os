# Code Review — Story 0025

## Async boundary ✅

- `resume()` no longer executes on the request thread: afterCommit only calls
  `resumeAsync`, which enqueues onto `scanDispatchExecutor`.
- Dispatch still begins strictly after the creating transaction commits
  (synchronization registered post-transaction; unchanged registration
  conditions for non-terminal scans).
- Worker is a Spring-managed executor bean with `destroyMethod="shutdown"`;
  no self-invocation (coordinator is a constructor-injected collaborator, not
  a proxied self-call).
- Worker does not inherit or require the request transaction; claim service
  opens its own transactional context as before.

## Correctness ✅

- Idempotency untouched: same key + fingerprint replays and — importantly —
  re-registers dispatch (documented recovery path).
- Claim semantics untouched: every child still passes through
  `ActiveScanDispatchClaimService.claimForDispatch`; no duplicate dispatch.
- Analysis execution semantics untouched (`dispatchRegistered` call identical).
- Sequential loop preserved inside the worker; no 1 436-way parallelization.

## UX contract ✅

- Runtime: 202 in 3.6 s on real Kraken scope (was ≈161 s/504); scanId delivered;
  Angular polling observed live (2 s cadence, all 200); terminal reached without
  any nginx timeout change (nginx config untouched).

## Lifecycle ✅

- Reconciliation behavior observed in runtime: DISPATCH_REQUESTED → RUNNING →
  COMPLETED driven by polling GETs; final state persisted.
- Verdict recorded: read-time reconciliation SUFFICIENT. No speculative
  lifecycle/write-back rewrite.

## Scope ✅

- Diff = 3 production files (config bean, coordinator, one-line call site) +
  tests + story docs. No strategy/risk/opportunity algorithm changes, no
  unrelated refactor, no frontend change.

## Test review

- New boundary test fails on pre-fix code (verified conceptually: blocking
  claim inside afterCommit would stall create()); uses latches, no sleeps.
- Existing assertion updated from sync to timeout-based `resumeAsync` verify:
  mechanism change only; business contract assertions intact.

**Verdict: approve.**
