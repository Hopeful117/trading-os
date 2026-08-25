# Repository Analysis — Story 0025

## DevLog contribution

- `get_engineering_context` (fresh, 60 evidences): did NOT surface the
  investigation report committed minutes earlier nor the dispatch coordinator —
  repository reading remains necessary for precise implementation work.
- `search_project_history("active scan")` (previous mission) provided the
  Story 0005/0006/0007/0012 skeleton; reused here as historical grounding.
- Honest evaluation: for this corrective story, DevLog added historical
  context but no implementation-critical detail beyond the repository.

## Facts confirmed on HEAD (all verified, none drifted from investigation)

| Fact | Location | Status |
|---|---|---|
| POST returns `202` + `Location` + body with `scanId` | `MarketIntelligenceController.createScan` L85–106 | ✅ |
| `afterCommit()` → `dispatchCoordinator.resume(scanId)` | `ActiveScanApplicationService.registerAfterCommitIfNeeded` L174–184 | ✅ |
| `resume()` = sequential eligible-market loop: claim → dispatchRegistered | `ActiveScanDispatchCoordinator.resume` | ✅ |
| Analyses async on virtual threads (`newVirtualThreadPerTaskExecutor`) | `IntelligenceExecutionConfiguration` L28–31 via `LocalAnalysisExecutionDispatcher.dispatch` | ✅ |
| Frontend/backend enums identical; polling exists (2 s interval token); 504→UNAVAILABLE mapping | `active-scan.model.ts`, `scan-poll-interval.ts`, `scan-panel.ts` | ✅ |
| Read-time reconciliation derives aggregate status from executions and can forward it monotonically (`floorStatus`+`persistForward`) | `ActiveScanReconciliationService.deriveStatus/persistForward` | ✅ |
| No production write of `ActiveScanMarketStatus` beyond REGISTERED/DISPATCH_REQUESTED | repo-wide grep (4+3 occurrences) | ✅ |

## Existing infrastructure audited for the async boundary

- `analysisExecutionDispatcherExecutor` (virtual-thread-per-task): owned by the
  analysis dispatcher's task map semantics — reusing it for scan-level resume
  would conflate two concerns and entangle shutdown/task-tracking. A sibling
  dedicated executor follows the established local pattern (AGENTS.md:
  "local virtual-thread execution in Market Intelligence").
- No durable queue/outbox/scheduler infra exists in-repo → introducing one is
  out of scope per story constraints.
- Recovery observation: `create()` re-registers afterCommit on idempotent
  replay (`existing != null` branch), so a lost dispatch can be retried by a
  legitimate client retry with the same `Idempotency-Key`. This is the current,
  honest resumability guarantee.

## Test harness available

`ActiveScanApplicationServiceTest` already drives real service code with
in-memory repositories, mocked coordinator, manual
`TransactionSynchronizationManager.initSynchronization()` and explicit
afterCommit triggering — ideal to prove the new boundary without Spring proxy
complexity or timing flakiness.

## Risks

- Async flip makes existing synchronous `verify(coordinator).resume(...)`
  assertions racy → must become `verify(timeout(…))`.
- Worker exceptions must not escape into unrelated virtual threads silently;
  log with scanId.
