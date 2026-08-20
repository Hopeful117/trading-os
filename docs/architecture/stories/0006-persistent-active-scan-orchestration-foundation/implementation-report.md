# Implementation Report — Story 0006

## Status

Implemented locally on branch
`story/0006-persistent-active-scan-orchestration-foundation`.

## Summary

Story 0006 now establishes the durable Active Scanner orchestration foundation
above single-market `AnalysisExecution`.

Implemented behavior:

- consumes Story 0005 scope resolution through
  `ActiveScanScopeResolutionService`;
- persists one `ActiveScan` per actor-scoped logical idempotent request;
- persists one `ActiveScanMarket` row per resolved candidate market;
- snapshots requested markets, candidate markets, eligibility decisions,
  effective markets and `resolvedAt` immutably;
- creates no child `AnalysisExecution` for excluded markets;
- registers or reuses exactly one logical `AnalysisExecution(ACTIVE)` per
  eligible market;
- persists scan, markets and child linkage before any dispatch occurs;
- separates `AnalysisExecution` registration from dispatch without breaking
  existing callers;
- performs durable post-commit dispatch claims through a dedicated
  `REQUIRES_NEW` application seam;
- atomically claims child handoff through `REQUESTED -> ACCEPTED` before
  dispatcher invocation;
- preserves at-least-once dispatch handoff with idempotent processing;
- exposes the minimum Story 0006 create/read scan API;
- hardens the ActiveScan trust boundary by removing normal host exposure of
  `market-intelligence`, keeping actor-owned scan endpoints behind the
  authenticated Gateway boundary;
- leaves PipelineRun, Observation, Opportunity, Trade Planning, Risk Domain,
  Broker execution and Passive Scanner semantics unchanged.

## Production Files Added

- `gateway/src/main/java/com/hope/trading/gateway/security/AuthenticatedActorHeaderFilter.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaActiveScanEntity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaActiveScanMarketEntity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaActiveScanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataActiveScanMarketRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataActiveScanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanResponse.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/CreateActiveScanRequestDto.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ActiveScanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanChildKeyFactory.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanDispatchClaimService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanDispatchCoordinator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanException.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanFingerprintFactory.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/CreateActiveScanCommand.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScan.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanDecisionSnapshot.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanMarket.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanMarketStatus.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanScopeSnapshot.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanStatus.java`
- `market-intelligence/src/main/resources/db/migration/V3__active_scan_orchestration_foundation.sql`

## Production Files Modified

- `docker-compose.yml`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/InMemoryAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceExceptionHandler.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/LocalAnalysisExecutionDispatcher.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AnalysisExecutionRepository.java`

## Test Files Added

- `gateway/src/test/java/com/hope/trading/gateway/security/AuthenticatedActorHeaderFilterTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/persistence/ActiveScanPersistenceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanControllerTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationServiceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/scan/ActiveScanDispatchClaimServiceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/scan/ActiveScanFingerprintFactoryTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanTest.java`

## Test Files Modified

- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanScopeResolutionControllerTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionServiceTest.java`

## Persistence Outcome

Added Flyway migration:

- `market-intelligence/src/main/resources/db/migration/V3__active_scan_orchestration_foundation.sql`

Created schema:

- `active_scans`
- `active_scan_markets`

Key constraints:

- `UNIQUE(actor_id, idempotency_key)`
- `UNIQUE(scan_id, market_id)`
- nullable `analysis_execution_id` for excluded markets
- FK `active_scan_markets.scan_id -> active_scans.scan_id`

## Idempotency and Dispatch Semantics

Scan-level idempotency:

- scoped by `(actor_id, idempotency_key)`;
- guarded by persisted `request_fingerprint`;
- same actor + key + same logical request reuses the same scan;
- same actor + key + different logical request raises
  `IDEMPOTENCY_CONFLICT`;
- different actors may reuse the same raw key independently.

Child-level logical identity:

- deterministic child key derived from `scanId + marketId + ACTIVE`;
- one logical eligible market maps to one logical child analysis;
- enforced by deterministic child idempotency plus `UNIQUE(scan_id, market_id)`.

Dispatch semantics:

- T1 persists scan, markets, child registrations and linkage before commit;
- `afterCommit()` only delegates coordination and does not perform implicit
  durable writes;
- durable child claim occurs in `REQUIRES_NEW`;
- child claim performs `REGISTERED -> DISPATCH_REQUESTED` and
  `REQUESTED -> ACCEPTED` before dispatcher invocation;
- dispatcher handoff is at-least-once;
- actual processing start is idempotent through atomic
  `ACCEPTED -> CONTEXT_BUILDING`.

Security boundary semantics:

- `AuthenticatedActorHeaderFilter` remains the authenticated Gateway adapter
  that derives `X-Actor-Id` from the trusted JWT principal and overwrites any
  client-supplied value for authenticated requests;
- `market-intelligence` does not add local Spring Security or duplicate JWT
  parsing;
- normal Docker Compose runtime no longer publishes `market-intelligence` on
  host port `8084`;
- external access to actor-owned ActiveScan endpoints is therefore expected to
  traverse `Gateway -> JWT authentication -> trusted actor propagation ->
  market-intelligence internal network`.

## Validation

Executed successfully:

- `docker compose config`
- `market-intelligence: mvn -q -Dtest='ActiveScanTest,ActiveScanFingerprintFactoryTest,ActiveScanApplicationServiceTest,ActiveScanDispatchClaimServiceTest,ActiveScanControllerTest,ActiveScanPersistenceTest,AnalysisExecutionServiceTest,ActiveScanScopeResolutionControllerTest' test`
- `market-intelligence: mvn -q -Dserver.port=0 test`
- `gateway: mvn -q test`

Observed validation notes:

- a plain `mvn -q test` in `market-intelligence` hit an existing test
  environment port collision on `8084` inside
  `RiskValidationAcknowledgmentPersistenceIntegrationTest`;
- rerunning the same full suite with `-Dserver.port=0` produced a clean pass
  without altering production or test code for that legacy port binding.
- final security verification identified a trust-boundary gap: actor-owned
  ActiveScan endpoints trusted Gateway-propagated actor identity correctly, but
  `market-intelligence` was still directly host-exposed on `8084`;
- the selected correction was to remove normal host exposure rather than add
  local Spring Security, because Gateway already owns authenticated external
  entry and internal Docker/Eureka communication does not require host
  publication.

## Documentation Reconciliation

Created:

- `docs/architecture/stories/0006-persistent-active-scan-orchestration-foundation/implementation-report.md`
- `docs/architecture/stories/0006-persistent-active-scan-orchestration-foundation/engineering-report.md`

## Deviations from Approved Plan

No architectural deviation from the approved Story 0006 plan.

Implementation detail worth noting:

- actor ownership for Story 0006 API is carried from gateway to
  `market-intelligence` through a dedicated `X-Actor-Id` forwarding filter,
  avoiding a premature security redesign inside `market-intelligence` while
  preserving actor-scoped idempotency and read ownership.
- final security hardening remained within the approved architecture by
  tightening deployment exposure rather than expanding service-local
  authentication scope.

## Unexpected Findings

- `market-intelligence` full-suite stability still depends on overriding the
  fixed test port for an older persistence integration test cluster.
- Mockito inline-agent warnings remain present in the existing build output but
  did not block validation.
- the original Story 0006 implementation was correct for authenticated Gateway
  traffic but unsafe as long as `market-intelligence` remained directly
  published on the host network.
