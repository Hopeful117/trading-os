package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.scan.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ActiveScanDispatchClaimServiceTest {
    private final Instant now = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void registeredChildIsDurablyClaimedBeforeDispatch() {
        FakeActiveScanRepository scans = new FakeActiveScanRepository(now);
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        UUID executionId = scans.registered.analysisExecutionId();
        UUID scanId = scans.scan.scanId();
        UUID scanMarketId = scans.registered.scanMarketId();

        when(executions.claimForDispatch(executionId)).thenReturn(true);
        when(executions.find(executionId)).thenReturn(execution(executionId, AnalysisExecutionStatus.ACCEPTED));

        ActiveScanDispatchClaimService service = new ActiveScanDispatchClaimService(
                scans,
                executions,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        ActiveScanDispatchClaimService.ClaimResult result = service.claimForDispatch(scanId, scanMarketId);

        assertThat(result.shouldDispatch()).isTrue();
        assertThat(scans.findMarketById(scanMarketId)).get().extracting(ActiveScanMarket::status)
                .isEqualTo(ActiveScanMarketStatus.DISPATCH_REQUESTED);
        assertThat(scans.findById(scanId)).get().extracting(ActiveScan::status)
                .isEqualTo(ActiveScanStatus.DISPATCH_REQUESTED);
        verify(executions).claimForDispatch(executionId);
    }

    @Test
    void secondResumeDoesNotPerformASecondInitialClaim() {
        FakeActiveScanRepository scans = new FakeActiveScanRepository(now);
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        UUID executionId = scans.registered.analysisExecutionId();

        when(executions.claimForDispatch(executionId)).thenReturn(true);
        when(executions.find(executionId)).thenReturn(execution(executionId, AnalysisExecutionStatus.ACCEPTED));

        ActiveScanDispatchClaimService service = new ActiveScanDispatchClaimService(
                scans,
                executions,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        service.claimForDispatch(scans.scan.scanId(), scans.registered.scanMarketId());
        service.claimForDispatch(scans.scan.scanId(), scans.registered.scanMarketId());

        verify(executions, times(1)).claimForDispatch(executionId);
    }

    private AnalysisExecution execution(UUID executionId, AnalysisExecutionStatus status) {
        AnalysisExecution base = AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("key-" + executionId),
                new com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionPolicy(
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(5),
                        0,
                        1,
                        new com.hope.trading.market_intelligence.domain.execution.ContextLimits(
                                10,
                                10,
                                10_000,
                                5,
                                com.hope.trading.market_intelligence.domain.context.ContextClassification.PUBLIC
                        ),
                        new com.hope.trading.market_intelligence.domain.execution.RetryPolicy(
                                0,
                                Duration.ZERO,
                                java.util.Set.of()
                        ),
                        java.util.Map.of(),
                        new com.hope.trading.market_intelligence.domain.execution.DegradationPolicy(
                                true,
                                true,
                                true,
                                true
                        )
                ),
                now,
                List.of(),
                new com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance(
                        UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan", "v1"
                ),
                new com.hope.trading.market_intelligence.domain.execution.AnalysisTraceMetadata(List.of())
        );
        return switch (status) {
            case REQUESTED -> base;
            case ACCEPTED -> base.transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1));
            default -> throw new IllegalArgumentException("Unsupported test status " + status);
        };
    }

    private static final class FakeActiveScanRepository implements ActiveScanRepository {
        private ActiveScan scan;
        private ActiveScanMarket registered;

        private FakeActiveScanRepository(Instant now) {
            UUID scanId = UUID.randomUUID();
            this.scan = ActiveScan.readyToDispatch(
                    scanId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "scan",
                    "scan-key",
                    "fingerprint",
                    new ActiveScanScopeSnapshot(List.of(), List.of(), List.of(), List.of(UUID.randomUUID()), now),
                    now
            );
            this.registered = ActiveScanMarket.registered(
                    UUID.randomUUID(),
                    scanId,
                    0,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    now
            );
        }

        @Override
        public ActiveScan save(ActiveScan scan) {
            this.scan = scan;
            return scan;
        }

        @Override
        public List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> markets) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ActiveScan> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ActiveScan> findByActorIdAndScanId(UUID actorId, UUID scanId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ActiveScan> findById(UUID scanId) {
            return scan.scanId().equals(scanId) ? Optional.of(scan) : Optional.empty();
        }

        @Override
        public List<ActiveScanMarket> findMarketsByScanId(UUID scanId) {
            return List.of(registered);
        }

        @Override
        public Optional<ActiveScanMarket> findMarketById(UUID scanMarketId) {
            return registered.scanMarketId().equals(scanMarketId)
                    ? Optional.of(registered)
                    : Optional.empty();
        }

        @Override
        public boolean transitionScanStatus(UUID scanId, ActiveScanStatus expected, ActiveScanStatus target, Instant updatedAt) {
            if (scan.scanId().equals(scanId) && scan.status() == expected && target == ActiveScanStatus.DISPATCH_REQUESTED) {
                scan = scan.markDispatchRequested(updatedAt);
                return true;
            }
            return false;
        }

        @Override
        public boolean transitionMarketStatus(UUID scanMarketId, ActiveScanMarketStatus expected, ActiveScanMarketStatus target, Instant updatedAt) {
            if (!registered.scanMarketId().equals(scanMarketId) || expected != ActiveScanMarketStatus.REGISTERED
                    || target != ActiveScanMarketStatus.DISPATCH_REQUESTED) {
                return false;
            }
            if (registered.status() != ActiveScanMarketStatus.REGISTERED) {
                return false;
            }
            registered = registered.markDispatchRequested(updatedAt);
            return true;
        }

        private Instant now() {
            return Instant.parse("2026-08-20T12:00:01Z");
        }

        @Override
        public List<ActiveScan> findRecentByActorId(UUID actorId, int limit) {
            return java.util.List.of(scan).stream()
                    .filter(s -> s.actorId().equals(actorId))
                    .sorted(Comparator
                            .comparing(ActiveScan::createdAt).reversed()
                            .thenComparing(ActiveScan::scanId).reversed())
                    .limit(limit)
                    .toList();
        }
    }
}
