package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.context.IntelligenceContextAssembler;
import com.hope.trading.market_intelligence.application.planning.ExecutionPlanner;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionPlan;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionStrategy;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CapabilityAnalysisCoordinatorTest {
    private final IntelligenceContextAssembler contexts = mock(IntelligenceContextAssembler.class);
    private final ArtifactPersistencePort artifacts = mock(ArtifactPersistencePort.class);
    private final ExecutionPlanner planner = mock(ExecutionPlanner.class);
    private final ExecutionEngine engine = mock(ExecutionEngine.class);
    private final CapabilityAnalysisCoordinator coordinator =
            new CapabilityAnalysisCoordinator(
                    new AnalysisStrategyRegistry(List.of(strategy())),
                    contexts,
                    artifacts,
                    planner,
                    engine,
                    Clock.fixed(Instant.parse("2026-08-01T12:00:01Z"), ZoneOffset.UTC)
            );

    @Test
    void unavailableCurrentSnapshotFailsBeforePlanningWithBoundedReason() {
        IntelligenceAnalysisRequest request = request();
        when(contexts.assemble(any(), any())).thenReturn(new IntelligenceContext(Map.of(
                ContextSectionType.MARKET_SNAPSHOT,
                ContextSection.unavailable(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
                        "Current market snapshot is unavailable")
        )));

        assertThatThrownBy(() -> coordinator.analyze(request.analysisId(), request))
                .isInstanceOf(AnalysisContextUnavailableException.class)
                .hasMessage("Current market snapshot is unavailable");
        verifyNoInteractions(planner);
        verifyNoInteractions(engine);
    }

    @Test
    void staleCurrentSnapshotFailsBeforePlanningWithBoundedReason() {
        IntelligenceAnalysisRequest request = request();
        MarketSnapshotContext payload = new MarketSnapshotContext(
                request.marketId(), "ETH/USD", null,
                java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, true,
                Instant.parse("2026-08-01T11:59:00Z"));
        when(contexts.assemble(any(), any())).thenReturn(new IntelligenceContext(Map.of(
                ContextSectionType.MARKET_SNAPSHOT,
                new ContextSection(
                        ContextSectionType.MARKET_SNAPSHOT,
                        ContextSectionStatus.STALE,
                        ContextSensitivity.PUBLIC,
                        payload,
                        new ContextProvenance(
                                "market-data",
                                payload.occurredAt(),
                                Instant.parse("2026-08-01T12:00:01Z")),
                        "Current market snapshot is stale")
        )));

        assertThatThrownBy(() -> coordinator.analyze(request.analysisId(), request))
                .isInstanceOf(AnalysisContextUnavailableException.class)
                .hasMessage("Current market snapshot is stale");
        verifyNoInteractions(planner);
        verifyNoInteractions(engine);
    }

    private AnalysisExecutionStrategy strategy() {
        return new AnalysisExecutionStrategy() {
            @Override
            public AnalysisExecutionMode mode() {
                return AnalysisExecutionMode.ACTIVE;
            }

            @Override
            public AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request) {
                return new AnalysisExecutionPlan(
                        List.of("spread-analysis"),
                        List.of(ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT)),
                        1,
                        Duration.ofSeconds(10)
                );
            }
        };
    }

    private IntelligenceAnalysisRequest request() {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AnalysisExecutionMode.ACTIVE,
                "objective"
        );
    }
}
