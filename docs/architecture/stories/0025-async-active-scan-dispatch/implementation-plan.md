# Implementation Plan — Story 0025

## Change set (backend only, Market Intelligence)

1. `IntelligenceExecutionConfiguration`: add `@Bean(name="scanDispatchExecutor",
   destroyMethod="shutdown") ExecutorService` → `newVirtualThreadPerTaskExecutor()`.
2. `ActiveScanDispatchCoordinator`:
   - inject the executor (constructor, `@Qualifier`);
   - add `resumeAsync(UUID scanId)`: `executor.execute(() -> { try { resume(scanId); }
     catch (RuntimeException e) { log.error("scan dispatch worker failed scanId={}", scanId, e); } })`;
   - `resume()` body unchanged (sequential claim+dispatch preserved).
3. `ActiveScanApplicationService.registerAfterCommitIfNeeded`: afterCommit now
   calls `coordinator.resumeAsync(scan.scanId())`. After-commit ordering
   semantics untouched (synchronization still registered only for
   non-terminal scans; still fires strictly post-commit).
4. No controller/Angular/nginx changes.

## Tests

- Update `ActiveScanApplicationServiceTest`: synchronous
  `verify(coordinator).resume(...)` → `verify(coordinator, timeout(2000)).resume(...)`
  wherever manual afterCommit is triggered.
- New `ActiveScanDispatchCoordinatorAsyncTest`:
  a) blocked-resume stub proves submit returns immediately and worker runs on a
     non-request thread (capture thread name ≠ caller);
  b) resume throwing propagates nothing to the submitting thread.
- New boundary test in `ActiveScanApplicationServiceTest`
  (`postCompletionIsDecoupledFromDispatchCompletion`): coordinator.resume blocks
  on a latch; create() must return while latch held; release; assert resume ran.

## Runtime validation protocol

1. Rebuild `market-intelligence` image; full stack up.
2. Direct HTTP: login (scanprobe), POST with Idempotency-Key, measure T0→202;
   capture Location + scanId; manual GET sequence sampling states.
3. Browser (Playwright): real UI run on Kraken scope; capture POST duration,
   GET poll cadence/statuses/bodies, panel state transitions to terminal;
   verify opportunities list refresh (Story 0022 integration).
4. DB timeline: active_scans status transitions via updated_at; executions
   counts; reconciliation verdict SUFFICIENT/INSUFFICIENT with evidence.
5. Quality gates: `mvnw -f market-intelligence/pom.xml test`; Angular
   `test:ci` + build (expect unchanged); `git diff --check`;
   docker compose build market-intelligence.

## Rollback

Single-commit backend change; revert restores previous behavior exactly.
