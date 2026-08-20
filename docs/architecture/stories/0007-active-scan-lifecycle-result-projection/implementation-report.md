# Implementation Report — Story 0007

## Status

Implemented locally on branch
`story/0007-active-scan-lifecycle-result-projection`.

## Summary

Story 0007 now turns the persisted Active Scan introduced by Story 0006 into a
truthful, pollable, trader-readable unit of work.

Implemented behavior:

- enriches the existing owned `GET /api/v1/intelligence/scans/{scanId}` API
  instead of adding a second result endpoint;
- extends `ActiveScanStatus` with `RUNNING`, `PARTIALLY_COMPLETED`,
  `COMPLETED`, and `FAILED`;
- preserves `READY_TO_DISPATCH`, `DISPATCH_REQUESTED`, and
  `COMPLETED_NO_WORK`;
- reconciles scan lifecycle synchronously on read from persisted child truth;
- keeps reconciliation deterministic, idempotent for unchanged child truth, and
  forward-only for persisted aggregate status;
- computes trader-facing progress counts without persisting duplicated
  counters;
- classifies per-market outcomes into excluded, running, no-opportunity,
  opportunity-found, failed, cancelled, and expired projections;
- distinguishes `AnalysisExecution.PARTIALLY_COMPLETED` from terminal
  `ActiveScan.PARTIALLY_COMPLETED`;
- treats pipeline `COMPLETED_NO_SIGNAL` as a valid successful analytical
  outcome rather than a failure;
- projects opportunities through existing lineage
  `AnalysisExecution -> PipelineRun -> TradingOpportunity`;
- avoids storing `opportunityId` on `ActiveScanMarket`;
- batch-loads linked executions, pipeline runs, and opportunities to avoid an
  obvious polling N+1 pattern;
- preserves Story 0005 scope authority and Story 0006 orchestration,
  idempotency, crash, retry, and trust-boundary guarantees.

## Production Files Added

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisPipelineRunViewRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AnalysisPipelineRunView.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AnalysisPipelineRunViewRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/TradingOpportunityVersionRef.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanMarketOutcome.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanReconciliationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanResultProjection.java`

## Production Files Modified

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/InMemoryAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/InMemoryTradingOpportunityRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaIntelligencePipelineRunEntity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaIntelligencePipelineRunRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradingOpportunityRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataTradingOpportunityRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanResponse.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/TradingOpportunityRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScan.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanStatus.java`

## Test Files Added

- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/persistence/ActiveScanProjectionPersistenceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/scan/ActiveScanReconciliationServiceTest.java`

## Test Files Modified

- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanControllerTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationServiceTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanTest.java`

## Lifecycle Semantics Implemented

Aggregate status semantics:

- `COMPLETED_NO_WORK`
  - zero eligible markets;
  - terminal successful no-work state.
- `RUNNING`
  - at least one eligible child remains unresolved;
  - includes scans where some market results are already visible.
- `COMPLETED`
  - all eligible children resolved;
  - no failure-class child outcome remains.
- `PARTIALLY_COMPLETED`
  - terminal;
  - all eligible children resolved;
  - at least one usable success survived;
  - at least one failure-class child resolved.
- `FAILED`
  - terminal;
  - all eligible children resolved into failure-class outcomes;
  - no usable success survived.

Story 0006 recovery states remain visible until child truth advances:

- `READY_TO_DISPATCH`
- `DISPATCH_REQUESTED`

## Reconciliation Algorithm

Owned GET now performs:

1. load actor-owned `ActiveScan`;
2. load ordered `ActiveScanMarket` rows by `ordinal`;
3. batch load linked `AnalysisExecution` rows;
4. batch load relevant `PipelineRun` rows for the production pipeline version;
5. batch load exact versioned `TradingOpportunity` rows;
6. classify each market outcome;
7. derive aggregate progress counts;
8. derive truthful aggregate lifecycle;
9. persist forward-only aggregate status when the derived lifecycle advances;
10. return the trader-facing projection.

Reconciliation guarantees:

- no child creation;
- no dispatch;
- no broker action;
- no Risk Domain action;
- repeated GET against unchanged persisted truth is idempotent.

## Child Classification Semantics

Scan-level child classes are derived from persisted truth:

- `REQUESTED` -> in-flight, not yet started;
- `ACCEPTED` -> in-flight, claimed for dispatch;
- `CONTEXT_BUILDING` / `RUNNING` / `AnalysisExecution.PARTIALLY_COMPLETED`
  -> in-flight;
- `FAILED` / `CANCELLED` / `EXPIRED` -> failure-class;
- `COMPLETED` -> requires downstream `ConsolidatedIntelligence` and
  `PipelineRun` classification:
  - `PipelineRun.COMPLETED_NO_SIGNAL` -> success with no opportunity;
  - `PipelineRun.COMPLETED` + opportunity lineage present -> success with
    opportunity;
  - `PipelineRun.FAILED_OBSERVATION` / `FAILED_OPPORTUNITY` -> failure-class;
  - missing expected result lineage -> failure-class diagnostic.

Important semantic distinction:

- `AnalysisExecution.PARTIALLY_COMPLETED` remains scan-level in-flight.
- `ActiveScan.PARTIALLY_COMPLETED` is terminal aggregate mixed-outcome state.

## Projection Shape

The enriched GET now returns:

- scan identity and context;
- reconciled aggregate status;
- timestamps;
- requested, candidate, and effective market ids from the persisted Story 0005
  snapshot;
- progress counts:
  - `totalCandidates`
  - `eligible`
  - `excluded`
  - `running`
  - `completed`
  - `failed`
  - `opportunitiesFound`
- ordered per-market results:
  - `ordinal`
  - `marketId`
  - `eligible`
  - `exclusionReasons`
  - `analysisExecutionId`
  - `analysisStatus`
  - `resultQuality`
  - trader-facing `outcome`
  - bounded diagnostic
  - projected opportunity when present.

## Persistence Outcome

No migration was added.

Verdict:

- `NO_MIGRATION_REQUIRED`

Reason:

- `ActiveScan.status` already persists as a string column large enough to store
  the added enum values;
- Story 0007 derives progress and result projection from existing persisted
  truth rather than introducing new stored counters or duplicated lineage.

## Validation

Focused Story 0007 and adjacent regression tests:

```bash
mvn -q -Dtest='ActiveScanTest,ActiveScanApplicationServiceTest,ActiveScanReconciliationServiceTest,ActiveScanControllerTest,ActiveScanPersistenceTest,ActiveScanProjectionPersistenceTest,AnalysisExecutionServiceTest' test
```

Result:

- pass

Full `market-intelligence` suite:

```bash
mvn -q -Dserver.port=0 test
```

Result:

- pass

Observed notes:

- Mockito inline-agent warnings remain in existing test output;
- Spring `open-in-view` warning remains present in existing test output;
- H2/Flyway compatibility warnings remain present in existing test output;
- no Story 0007 test required a production or test-code workaround.

## Runtime Validation

Runtime verification was executed as far as the current local environment
allowed.

Confirmed:

- `market-intelligence` service was running inside Docker Compose and no longer
  exposed on host port `8084`;
- Gateway remained published on `8080`;
- direct unauthenticated runtime attempt through Gateway to
  `/api/v1/users/register` returned `503 Service Unavailable` because
  `trading-app` was not currently available behind Gateway;
- attempting to start `trading-app` via normal `docker compose up -d
  trading-app` failed during image build because the service Docker build could
  not resolve local Maven dependency
  `com.hope.trading:risk-domain:jar:0.0.1-SNAPSHOT`.

Runtime benchmark implication:

- Story 0007 runtime proof through authenticated Gateway polling is only
  **partially** available in the current environment;
- Story 0007 logic itself is test-confirmed locally;
- the blocked E2E path is caused by local runtime composition/build state
  outside `market-intelligence` Story 0007 code.

## Compatibility

Preserved:

- Story 0005 deterministic scope authority;
- Story 0006 scan creation, fingerprinting, child idempotency, after-commit
  dispatch, and crash/retry guarantees;
- existing `AnalysisExecution` ownership;
- existing `PipelineRun` ownership;
- existing `TradingOpportunity` ownership;
- ADR-033 Active vs Passive boundary;
- Gateway trust-boundary model from Story 0006 security hardening.

No impact:

- Passive Scanner behavior;
- Risk Domain;
- Broker execution;
- AI orchestration or scoring.

## Deviations from Approved Plan

No architectural deviation from the approved Story 0007 plan.

Implementation detail worth noting:

- the GET create response now also goes through the owned projection path, so
  the POST response and subsequent polling response share the same read-side
  semantics immediately after creation.

## Unexpected Findings

- runtime Gateway proof is currently limited by local `trading-app` container
  availability rather than Story 0007 logic;
- `trading-app` Docker build currently depends on a locally unavailable
  `risk-domain` artifact, which blocks a fresh E2E runtime boot in this
  environment;
- existing full-suite logs include warnings unrelated to Story 0007 but do not
  invalidate the passing result.
