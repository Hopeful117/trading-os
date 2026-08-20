package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryTradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.application.pipeline.ProductionIntelligencePipeline;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisPipelineRunView;
import com.hope.trading.market_intelligence.application.port.AnalysisPipelineRunViewRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityScore;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveScanReconciliationServiceTest {
    private final Instant now = Instant.parse("2026-08-21T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final InMemoryActiveScanRepository scans = new InMemoryActiveScanRepository();
    private final InMemoryAnalysisExecutionRepository executions = new InMemoryAnalysisExecutionRepository();
    private final InMemoryPipelineRunRepository pipelineRuns = new InMemoryPipelineRunRepository();
    private final InMemoryTradingOpportunityRepository opportunities = new InMemoryTradingOpportunityRepository();
    private final ActiveScanReconciliationService service = new ActiveScanReconciliationService(
            scans,
            executions,
            pipelineRuns,
            opportunities,
            clock
    );

    @Test
    void completedNoWorkRemainsTerminalAndExcludedMarketVisible() {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        scans.save(ActiveScan.completedNoWork(
                scanId,
                actorId,
                UUID.randomUUID(),
                "scan",
                "key",
                "fingerprint",
                snapshot(List.of(
                        new ActiveScanDecisionSnapshot(
                                marketId, "ALT/USD", "KRAKEN", false,
                                List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE)
                        )
                ), List.of()),
                now
        ));
        scans.saveMarkets(List.of(ActiveScanMarket.excluded(
                UUID.randomUUID(),
                scanId,
                0,
                marketId,
                List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE),
                now
        )));

        ActiveScanResultProjection projection = service.reconcileOwned(actorId, scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.COMPLETED_NO_WORK);
        assertThat(projection.progress().eligible()).isZero();
        assertThat(projection.markets()).singleElement()
                .extracting(ActiveScanResultProjection.MarketResult::outcome)
                .isEqualTo(ActiveScanMarketOutcome.EXCLUDED);
    }

    @Test
    void requestedChildKeepsReadyToDispatch() {
        ScanFixture fixture = persistSingleEligibleScan(ActiveScanStatus.READY_TO_DISPATCH, requestedExecution());

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.READY_TO_DISPATCH);
        assertThat(projection.progress().running()).isEqualTo(1);
        assertThat(projection.markets()).singleElement()
                .extracting(ActiveScanResultProjection.MarketResult::outcome)
                .isEqualTo(ActiveScanMarketOutcome.RUNNING);
    }

    @Test
    void acceptedChildKeepsDispatchRequested() {
        ScanFixture fixture = persistSingleEligibleScan(ActiveScanStatus.DISPATCH_REQUESTED, acceptedExecution());

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.DISPATCH_REQUESTED);
    }

    @Test
    void childPartiallyCompletedExecutionIsStillScanLevelRunning() {
        ScanFixture fixture = persistSingleEligibleScan(ActiveScanStatus.DISPATCH_REQUESTED, partiallyCompletedExecution());

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.RUNNING);
        assertThat(projection.markets()).singleElement()
                .extracting(ActiveScanResultProjection.MarketResult::analysisStatus)
                .isEqualTo(AnalysisExecutionStatus.PARTIALLY_COMPLETED);
    }

    @Test
    void completedNoSignalIsSuccessfulWithoutOpportunity() {
        AnalysisExecution execution = completedExecution(
                IntelligenceExecutionStatus.COMPLETE,
                AnalysisResultQuality.COMPLETE
        );
        ScanFixture fixture = persistSingleEligibleScan(ActiveScanStatus.DISPATCH_REQUESTED, execution);
        pipelineRuns.put(new AnalysisPipelineRunView(
                execution.executionId(),
                ProductionIntelligencePipeline.VERSION,
                "COMPLETED_NO_SIGNAL",
                null,
                null,
                "NO_SIGNAL",
                "No opportunity matched current market conditions",
                now
        ));

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.COMPLETED);
        assertThat(projection.progress().completed()).isEqualTo(1);
        assertThat(projection.progress().opportunitiesFound()).isZero();
        assertThat(projection.markets()).singleElement()
                .satisfies(result -> {
                    assertThat(result.outcome()).isEqualTo(ActiveScanMarketOutcome.COMPLETED_NO_OPPORTUNITY);
                    assertThat(result.opportunity()).isNull();
                });
    }

    @Test
    void completedOpportunityIsProjected() {
        AnalysisExecution execution = completedExecution(
                IntelligenceExecutionStatus.COMPLETE,
                AnalysisResultQuality.COMPLETE
        );
        ScanFixture fixture = persistSingleEligibleScan(ActiveScanStatus.DISPATCH_REQUESTED, execution);
        OpportunityId opportunityId = new OpportunityId(UUID.randomUUID());
        TradingOpportunity opportunity = OpportunityTestFixtures.opportunity(
                opportunityId,
                1,
                OpportunityStatus.ACTIVE,
                new OpportunityScore(new BigDecimal("82.50")),
                now
        );
        opportunities.append(opportunity);
        pipelineRuns.put(new AnalysisPipelineRunView(
                execution.executionId(),
                ProductionIntelligencePipeline.VERSION,
                "COMPLETED",
                opportunity.id().value(),
                opportunity.version().value(),
                null,
                null,
                now
        ));

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.COMPLETED);
        assertThat(projection.progress().opportunitiesFound()).isEqualTo(1);
        assertThat(projection.markets()).singleElement()
                .satisfies(result -> {
                    assertThat(result.outcome()).isEqualTo(ActiveScanMarketOutcome.OPPORTUNITY_FOUND);
                    assertThat(result.opportunity()).isNotNull();
                    assertThat(result.opportunity().score().value()).isEqualByComparingTo("82.50");
                });
    }

    @Test
    void mixedSuccessAndFailureBecomesPartiallyCompleted() {
        AnalysisExecution success = completedExecution(
                IntelligenceExecutionStatus.COMPLETE,
                AnalysisResultQuality.COMPLETE
        );
        AnalysisExecution failed = failedExecution();
        ScanFixture fixture = persistTwoEligibleScan(
                ActiveScanStatus.RUNNING,
                List.of(success, failed)
        );
        pipelineRuns.put(new AnalysisPipelineRunView(
                success.executionId(),
                ProductionIntelligencePipeline.VERSION,
                "COMPLETED_NO_SIGNAL",
                null,
                null,
                "NO_SIGNAL",
                "No opportunity",
                now
        ));

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.PARTIALLY_COMPLETED);
        assertThat(projection.progress().completed()).isEqualTo(1);
        assertThat(projection.progress().failed()).isEqualTo(1);
    }

    @Test
    void allFailureOutcomesBecomeFailed() {
        AnalysisExecution cancelled = cancelledExecution();
        AnalysisExecution expired = expiredExecution();
        ScanFixture fixture = persistTwoEligibleScan(
                ActiveScanStatus.RUNNING,
                List.of(cancelled, expired)
        );

        ActiveScanResultProjection projection = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(projection.status()).isEqualTo(ActiveScanStatus.FAILED);
        assertThat(projection.progress().failed()).isEqualTo(2);
        assertThat(projection.markets()).extracting(ActiveScanResultProjection.MarketResult::outcome)
                .containsExactly(ActiveScanMarketOutcome.CANCELLED, ActiveScanMarketOutcome.EXPIRED);
    }

    @Test
    void repeatedReconciliationIsIdempotentAndTerminalStatusDoesNotRegress() {
        ScanFixture fixture = persistSingleEligibleScan(ActiveScanStatus.COMPLETED, requestedExecution());

        ActiveScanResultProjection first = service.reconcileOwned(fixture.actorId, fixture.scanId);
        ActiveScanResultProjection second = service.reconcileOwned(fixture.actorId, fixture.scanId);

        assertThat(first.status()).isEqualTo(ActiveScanStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(ActiveScanStatus.COMPLETED);
        assertThat(second.progress()).isEqualTo(first.progress());
        assertThat(second.markets()).isEqualTo(first.markets());
    }

    private ScanFixture persistSingleEligibleScan(ActiveScanStatus status, AnalysisExecution execution) {
        return persistTwoEligibleScan(status, List.of(execution));
    }

    private ScanFixture persistTwoEligibleScan(ActiveScanStatus status, List<AnalysisExecution> children) {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        List<ActiveScanDecisionSnapshot> decisions = new ArrayList<>();
        List<UUID> effective = new ArrayList<>();
        List<ActiveScanMarket> markets = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            AnalysisExecution child = children.get(i);
            executions.save(child);
            UUID marketId = child.provenance().marketId();
            decisions.add(new ActiveScanDecisionSnapshot(marketId, "MKT-" + i, "KRAKEN", true, List.of()));
            effective.add(marketId);
            markets.add(ActiveScanMarket.registered(
                    UUID.randomUUID(),
                    scanId,
                    i,
                    marketId,
                    child.executionId(),
                    now
            ));
        }
        scans.save(ActiveScan.restore(
                scanId,
                actorId,
                accountId,
                "scan",
                "key-" + scanId,
                "fingerprint-" + scanId,
                snapshot(decisions, effective),
                status,
                now,
                now,
                now
        ));
        scans.saveMarkets(markets);
        return new ScanFixture(actorId, scanId);
    }

    private ActiveScanScopeSnapshot snapshot(List<ActiveScanDecisionSnapshot> decisions, List<UUID> effective) {
        List<UUID> markets = decisions.stream().map(ActiveScanDecisionSnapshot::marketId).toList();
        return new ActiveScanScopeSnapshot(markets, markets, decisions, effective, now);
    }

    private AnalysisExecution requestedExecution() {
        return baseRequested(UUID.randomUUID(), UUID.randomUUID());
    }

    private AnalysisExecution acceptedExecution() {
        return requestedExecution().transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1));
    }

    private AnalysisExecution partiallyCompletedExecution() {
        AnalysisExecution running = runningExecution();
        return running.partiallyComplete(
                result(running.executionId(), running.provenance().marketId(), IntelligenceExecutionStatus.PARTIAL),
                AnalysisResultQuality.PARTIAL,
                now.plusSeconds(4)
        );
    }

    private AnalysisExecution completedExecution(
            IntelligenceExecutionStatus resultStatus,
            AnalysisResultQuality quality
    ) {
        AnalysisExecution running = runningExecution();
        return running.complete(
                result(running.executionId(), running.provenance().marketId(), resultStatus),
                quality,
                now.plusSeconds(4)
        );
    }

    private AnalysisExecution failedExecution() {
        return runningExecution().transitionTo(AnalysisExecutionStatus.FAILED, now.plusSeconds(4));
    }

    private AnalysisExecution cancelledExecution() {
        return runningExecution().transitionTo(AnalysisExecutionStatus.CANCELLED, now.plusSeconds(4));
    }

    private AnalysisExecution expiredExecution() {
        return runningExecution().transitionTo(AnalysisExecutionStatus.EXPIRED, now.plusSeconds(4));
    }

    private AnalysisExecution runningExecution() {
        return acceptedExecution()
                .transitionTo(AnalysisExecutionStatus.CONTEXT_BUILDING, now.plusSeconds(2))
                .transitionTo(AnalysisExecutionStatus.RUNNING, now.plusSeconds(3));
    }

    private AnalysisExecution baseRequested(UUID executionId, UUID marketId) {
        return AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("key-" + executionId),
                ExecutionTestFixtures.policy(),
                now,
                List.of("deterministic-active"),
                new AnalysisExecutionProvenance(
                        marketId,
                        AnalysisExecutionMode.ACTIVE,
                        "scan",
                        "v1"
                ),
                AnalysisTraceMetadata.empty()
        );
    }

    private ConsolidatedIntelligence result(
            UUID executionId,
            UUID marketId,
            IntelligenceExecutionStatus status
    ) {
        ConsolidatedIntelligence complete = ExecutionTestFixtures.result(executionId, marketId, now);
        return new ConsolidatedIntelligence(
                complete.analysisId(),
                complete.marketId(),
                AnalysisExecutionMode.ACTIVE,
                status,
                complete.contextSections(),
                complete.findings(),
                complete.warnings(),
                complete.executionMetadata()
        );
    }

    private record ScanFixture(UUID actorId, UUID scanId) {
    }

    private static final class InMemoryPipelineRunRepository implements AnalysisPipelineRunViewRepository {
        private final Map<UUID, AnalysisPipelineRunView> runs = new LinkedHashMap<>();

        void put(AnalysisPipelineRunView run) {
            runs.put(run.analysisExecutionId(), run);
        }

        @Override
        public List<AnalysisPipelineRunView> findByAnalysisExecutionIdsAndPipelineVersion(
                Collection<UUID> analysisExecutionIds,
                String pipelineVersion
        ) {
            return analysisExecutionIds.stream()
                    .map(runs::get)
                    .filter(Objects::nonNull)
                    .filter(run -> run.pipelineVersion().equals(pipelineVersion))
                    .toList();
        }
    }

    private static final class InMemoryActiveScanRepository implements ActiveScanRepository {
        private final Map<UUID, ActiveScan> scans = new LinkedHashMap<>();
        private final Map<UUID, ActiveScanMarket> markets = new LinkedHashMap<>();

        @Override
        public ActiveScan save(ActiveScan scan) {
            scans.put(scan.scanId(), scan);
            return scan;
        }

        @Override
        public List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> values) {
            values.forEach(market -> markets.put(market.scanMarketId(), market));
            return values;
        }

        @Override
        public Optional<ActiveScan> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey) {
            return scans.values().stream()
                    .filter(scan -> scan.actorId().equals(actorId))
                    .filter(scan -> scan.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public Optional<ActiveScan> findByActorIdAndScanId(UUID actorId, UUID scanId) {
            return findById(scanId).filter(scan -> scan.actorId().equals(actorId));
        }

        @Override
        public Optional<ActiveScan> findById(UUID scanId) {
            return Optional.ofNullable(scans.get(scanId));
        }

        @Override
        public List<ActiveScanMarket> findMarketsByScanId(UUID scanId) {
            return markets.values().stream()
                    .filter(market -> market.scanId().equals(scanId))
                    .sorted(Comparator.comparingInt(ActiveScanMarket::ordinal))
                    .toList();
        }

        @Override
        public Optional<ActiveScanMarket> findMarketById(UUID scanMarketId) {
            return Optional.ofNullable(markets.get(scanMarketId));
        }

        @Override
        public boolean transitionScanStatus(UUID scanId, ActiveScanStatus expected, ActiveScanStatus target, Instant updatedAt) {
            ActiveScan current = scans.get(scanId);
            if (current == null || current.status() != expected) {
                return false;
            }
            scans.put(scanId, current.reconcileTo(target, updatedAt));
            return true;
        }

        @Override
        public boolean transitionMarketStatus(
                UUID scanMarketId,
                ActiveScanMarketStatus expected,
                ActiveScanMarketStatus target,
                Instant updatedAt
        ) {
            ActiveScanMarket current = markets.get(scanMarketId);
            if (current == null || current.status() != expected || target != ActiveScanMarketStatus.DISPATCH_REQUESTED) {
                return false;
            }
            markets.put(scanMarketId, current.markDispatchRequested(updatedAt));
            return true;
        }
    }
}
