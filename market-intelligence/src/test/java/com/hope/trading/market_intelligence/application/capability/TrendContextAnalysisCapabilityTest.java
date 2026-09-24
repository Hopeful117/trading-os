package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.adapter.marketdata.TrendContextInputMapper;
import com.hope.trading.market_intelligence.adapter.marketdata.TrendContextRoleHistory;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import com.hope.trading.market_intelligence.application.planning.CapabilityRegistry;
import com.hope.trading.market_intelligence.application.planning.ArtifactAdapterRegistry;
import com.hope.trading.market_intelligence.application.planning.ExecutionPlanner;
import com.hope.trading.market_intelligence.application.planning.PlanningRequest;
import com.hope.trading.market_intelligence.domain.planning.ArtifactDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrendContextAnalysisCapabilityTest {
    private static final UUID ANALYSIS_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @Test
    void isRegisteredWithTheProductionPlannerAndExecutesTypedAssessmentArtifact() {
        TrendContextInputMapper mapper = mock(TrendContextInputMapper.class);
        TrendContextEngine engine = mock(TrendContextEngine.class);
        TrendContextProfile profile = TrendContextTestFixtures.profile();
        TrendContextAssessmentInput input = mock(TrendContextAssessmentInput.class);
        TrendContextAssessment assessment = TrendContextTestFixtures.assessment();
        when(input.roleSeries()).thenReturn(Map.of());
        when(input.fingerprint()).thenReturn(assessment.inputFingerprint());
        when(input.assessmentAt()).thenReturn(assessment.assessmentAt());
        when(input.cutOffAt()).thenReturn(assessment.cutOffAt());
        when(mapper.map(any(), same(profile), any(), any(), any())).thenReturn(input);
        when(engine.assess(input)).thenReturn(assessment);

        TrendContextAnalysisCapability capability =
                new TrendContextAnalysisCapability(mapper, engine, profile);
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(capability);

        assertThat(registry.find(new CapabilityId(
                TrendContextAnalysisCapability.CAPABILITY_ID),
                new CapabilityVersion(TrendContextAnalysisCapability.CAPABILITY_VERSION)))
                .containsSame(capability);
        assertThat(capability.supportedModes()).containsExactlyInAnyOrder(
                AnalysisExecutionMode.ACTIVE, AnalysisExecutionMode.PASSIVE);
        assertThat(capability.metadata().requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.artifactType()).isEqualTo(ProductionArtifactTypes.TREND_CONTEXT_HISTORY);
            assertThat(requirement.required()).isTrue();
        });
        var plan = new ExecutionPlanner(registry, new ArtifactAdapterRegistry(),
                java.time.Clock.systemUTC()).plan(new PlanningRequest(
                ANALYSIS_ID,
                Set.of(new CapabilityId(TrendContextAnalysisCapability.CAPABILITY_ID)),
                Set.of(),
                Set.of(new ArtifactDescriptor(ProductionArtifactTypes.TREND_CONTEXT_HISTORY,
                        ProductionArtifactTypes.V1))));
        assertThat(plan.nodes().values()).singleElement()
                .extracting(node -> node.capability().metadata().id().value())
                .isEqualTo(TrendContextAnalysisCapability.CAPABILITY_ID);

        StoredArtifact history = new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity("trend-context-history", "test", "1.0.0"),
                        ArtifactScope.publicMarket(TrendContextTestFixtures.MARKET_ID,
                                "TREND_CONTEXT", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.empty()),
                new TrendContextRoleHistory(Map.of(), assessment.assessmentAt(), assessment.cutOffAt(),
                        profile.profileId(), profile.profileVersion(), "trend-context-rules-v1", Map.of()),
                ArtifactFreshness.validUntil(assessment.assessmentAt(),
                        assessment.assessmentAt().plusSeconds(60), "source"),
                new ArtifactProvenance("test", "1.0.0", EXECUTION_ID,
                        assessment.assessmentAt(), Set.of(), Set.of()),
                AnalysisResultQuality.COMPLETE);
        CapabilityContext context = new CapabilityContext(
                ANALYSIS_ID, EXECUTION_ID,
                Map.of(capability.metadata().requirements().getFirst(), List.of(history)),
                Set.of(), Map.of(), List.of(), mock(CancellationToken.class));

        CapabilityResult result = capability.execute(context);

        assertThat(result.completeness()).isEqualTo(CapabilityCompleteness.COMPLETE);
        assertThat(result.artifacts()).singleElement().satisfies(output -> {
            assertThat(output.type()).isEqualTo(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT);
            TrendContextCapabilityContent content =
                    (TrendContextCapabilityContent) output.artifact().content();
            assertThat(content.assessment().attention()).isEqualTo(assessment.attention());
            assertThat(content.inputFingerprint()).isEqualTo(assessment.inputFingerprint());
            assertThat(content.assessmentFingerprint()).isEqualTo(assessment.assessmentFingerprint());
        });
        assertThat(capability.analyze(null, null).findings()).isEmpty();
    }

    @Test
    void missingHistoryIsDegradedWithoutFabricatingAnAssessment() {
        TrendContextAnalysisCapability capability = new TrendContextAnalysisCapability(
                new TrendContextInputMapper(), new TrendContextEngine(), TrendContextTestFixtures.profile());
        com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement requirement =
                capability.metadata().requirements().getFirst();
        CapabilityResult result = capability.execute(new CapabilityContext(
                ANALYSIS_ID, EXECUTION_ID, Map.of(), Set.of(requirement), Map.of(),
                List.of(), mock(CancellationToken.class)));

        assertThat(result.completeness()).isEqualTo(CapabilityCompleteness.DEGRADED);
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.diagnostics()).noneMatch(finding -> finding.contains("UNKNOWN"));
    }
}
