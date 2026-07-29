package com.hope.trading.market_intelligence.application.orchestration;

import com.hope.trading.market_intelligence.adapter.ai.DisabledAiEngineAdapter;
import com.hope.trading.market_intelligence.application.capability.*;
import com.hope.trading.market_intelligence.application.context.*;
import com.hope.trading.market_intelligence.application.strategy.*;
import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceOrchestratorTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void unavailableAiDoesNotBreakDeterministicAnalysisOrRequestAccountContext() {
        AtomicInteger accountCalls = new AtomicInteger();
        SpreadAnalysisCapability spread = new SpreadAnalysisCapability();
        DisabledAiAnalysisCapability ai =
                new DisabledAiAnalysisCapability(new DisabledAiEngineAdapter());
        AnalysisExecutionStrategy strategy = strategy(
                AnalysisExecutionMode.PASSIVE,
                List.of(spread.id(), ai.id()),
                publicBaseline(),
                Duration.ofMillis(200)
        );
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy,
                List.of(spread, ai),
                List.of(identityContributor(), snapshotContributor(), accountContributor(accountCalls))
        );

        ConsolidatedIntelligence result = orchestrator.analyze(request(AnalysisExecutionMode.PASSIVE));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().getFirst().origin())
                .isEqualTo(AnalysisOrigin.DETERMINISTIC);
        assertThat(result.executionMetadata().capabilityExecutions())
                .anyMatch(execution -> execution.origin() == AnalysisOrigin.AI
                        && execution.status() == CapabilityExecutionStatus.UNAVAILABLE);
        assertThat(accountCalls).hasValue(0);
        assertThat(result.status()).isEqualTo(IntelligenceExecutionStatus.PARTIAL);
    }

    @Test
    void partialContextProducesExplicitDegradedResult() {
        SpreadAnalysisCapability spread = new SpreadAnalysisCapability();
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy(
                        AnalysisExecutionMode.PASSIVE,
                        List.of(spread.id()),
                        publicBaseline(),
                        Duration.ofMillis(200)
                ),
                List.of(spread),
                List.of(identityContributor())
        );

        ConsolidatedIntelligence result = orchestrator.analyze(request(AnalysisExecutionMode.PASSIVE));

        assertThat(result.status()).isEqualTo(IntelligenceExecutionStatus.DEGRADED);
        assertThat(result.contextSections())
                .anyMatch(section -> section.type() == ContextSectionType.MARKET_SNAPSHOT
                        && section.status() == ContextSectionStatus.MISSING);
    }

    @Test
    void missingOptionalContextProducesExplicitPartialResult() {
        SpreadAnalysisCapability spread = new SpreadAnalysisCapability();
        AnalysisExecutionStrategy strategy = strategy(
                AnalysisExecutionMode.PASSIVE,
                List.of(spread.id()),
                List.of(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
                        ContextRequirement.optionalPublic(ContextSectionType.NEWS)
                ),
                Duration.ofMillis(200)
        );
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy,
                List.of(spread),
                List.of(snapshotContributor())
        );

        ConsolidatedIntelligence result = orchestrator.analyze(request(AnalysisExecutionMode.PASSIVE));

        assertThat(result.status()).isEqualTo(IntelligenceExecutionStatus.PARTIAL);
        assertThat(result.contextSections())
                .anyMatch(section -> section.type() == ContextSectionType.NEWS
                        && section.status() == ContextSectionStatus.MISSING);
    }

    @Test
    void oneCapabilityFailureDoesNotDiscardSuccessfulFindings() {
        SpreadAnalysisCapability spread = new SpreadAnalysisCapability();
        AnalysisCapability failing = capability(
                "failing", AnalysisOrigin.DETERMINISTIC,
                context -> {
                    throw new IllegalStateException("boom");
                }
        );
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy(
                        AnalysisExecutionMode.ACTIVE,
                        List.of(spread.id(), failing.id()),
                        publicBaseline(),
                        Duration.ofMillis(200)
                ),
                List.of(spread, failing),
                List.of(identityContributor(), snapshotContributor())
        );

        ConsolidatedIntelligence result = orchestrator.analyze(request(AnalysisExecutionMode.ACTIVE));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.status()).isEqualTo(IntelligenceExecutionStatus.PARTIAL);
        assertThat(result.executionMetadata().capabilityExecutions())
                .anyMatch(execution -> execution.capabilityId().equals("failing")
                        && execution.status() == CapabilityExecutionStatus.FAILED);
    }

    @Test
    void timeoutIsReportedWithoutFailingTheWholeAnalysis() {
        SpreadAnalysisCapability spread = new SpreadAnalysisCapability();
        AnalysisCapability slow = capability(
                "slow", AnalysisOrigin.DETERMINISTIC,
                context -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return CapabilityAnalysisResult.empty("late");
                }
        );
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy(
                        AnalysisExecutionMode.ACTIVE,
                        List.of(spread.id(), slow.id()),
                        publicBaseline(),
                        Duration.ofMillis(25)
                ),
                List.of(spread, slow),
                List.of(identityContributor(), snapshotContributor())
        );

        ConsolidatedIntelligence result = orchestrator.analyze(request(AnalysisExecutionMode.ACTIVE));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.executionMetadata().capabilityExecutions())
                .anyMatch(execution -> execution.capabilityId().equals("slow")
                        && execution.status() == CapabilityExecutionStatus.TIMED_OUT);
    }

    @Test
    void deterministicAndAiProvenanceRemainDistinctDuringConsolidation() {
        AnalysisCapability deterministic = findingCapability(
                "deterministic", AnalysisOrigin.DETERMINISTIC,
                FindingType.DETERMINISTIC_FINDING
        );
        AnalysisCapability ai = findingCapability(
                "ai", AnalysisOrigin.AI, FindingType.HYPOTHESIS
        );
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy(
                        AnalysisExecutionMode.ACTIVE,
                        List.of(deterministic.id(), ai.id()),
                        publicBaseline(),
                        Duration.ofMillis(200)
                ),
                List.of(deterministic, ai),
                List.of(identityContributor(), snapshotContributor())
        );

        ConsolidatedIntelligence result = orchestrator.analyze(request(AnalysisExecutionMode.ACTIVE));

        assertThat(result.findings())
                .extracting(IntelligenceFinding::origin)
                .containsExactlyInAnyOrder(AnalysisOrigin.DETERMINISTIC, AnalysisOrigin.AI);
        assertThat(result.findings().stream()
                .filter(finding -> finding.origin() == AnalysisOrigin.AI)
                .map(IntelligenceFinding::type))
                .containsExactly(FindingType.HYPOTHESIS);
    }

    @Test
    void capabilityReceivesOnlyTheContextSectionsItDeclared() {
        AtomicReference<Set<ContextSectionType>> exposed = new AtomicReference<>();
        AtomicInteger accountCalls = new AtomicInteger();
        AnalysisCapability inspecting = capability(
                "inspecting", AnalysisOrigin.DETERMINISTIC,
                context -> {
                    exposed.set(context.sections().keySet());
                    return new CapabilityAnalysisResult(List.of(), List.of());
                }
        );
        AnalysisExecutionStrategy strategy = strategy(
                AnalysisExecutionMode.ACTIVE,
                List.of(inspecting.id()),
                List.of(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
                        new ContextRequirement(
                                ContextSectionType.ACCOUNT,
                                true,
                                ContextSensitivity.USER_PRIVATE
                        )
                ),
                Duration.ofMillis(200)
        );
        IntelligenceOrchestrator orchestrator = orchestrator(
                strategy,
                List.of(inspecting),
                List.of(snapshotContributor(), accountContributor(accountCalls))
        );

        orchestrator.analyze(request(AnalysisExecutionMode.ACTIVE));

        assertThat(accountCalls).hasValue(1);
        assertThat(exposed.get()).containsExactly(ContextSectionType.MARKET_SNAPSHOT);
    }

    private IntelligenceOrchestrator orchestrator(
            AnalysisExecutionStrategy strategy,
            List<AnalysisCapability> capabilities,
            List<ContextContributor> contributors
    ) {
        return new IntelligenceOrchestrator(
                new IntelligenceContextAssembler(contributors),
                new AnalysisStrategyRegistry(List.of(strategy)),
                capabilities,
                new IntelligenceConsolidator(),
                executor
        );
    }

    private AnalysisExecutionStrategy strategy(
            AnalysisExecutionMode mode,
            List<String> capabilityIds,
            List<ContextRequirement> requirements,
            Duration timeout
    ) {
        return new AnalysisExecutionStrategy() {
            @Override
            public AnalysisExecutionMode mode() {
                return mode;
            }

            @Override
            public AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request) {
                return new AnalysisExecutionPlan(
                        capabilityIds, requirements, capabilityIds.size(), timeout
                );
            }
        };
    }

    private AnalysisCapability capability(
            String id,
            AnalysisOrigin origin,
            java.util.function.Function<IntelligenceContext, CapabilityAnalysisResult> action
    ) {
        return new AnalysisCapability() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public AnalysisOrigin origin() {
                return origin;
            }

            @Override
            public Set<AnalysisExecutionMode> supportedModes() {
                return Set.of(AnalysisExecutionMode.PASSIVE, AnalysisExecutionMode.ACTIVE);
            }

            @Override
            public List<ContextRequirement> requirements(AnalysisExecutionMode mode) {
                return List.of(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT)
                );
            }

            @Override
            public CapabilityAvailability availability() {
                return CapabilityAvailability.AVAILABLE;
            }

            @Override
            public CapabilityAnalysisResult analyze(
                    IntelligenceAnalysisRequest request,
                    IntelligenceContext context
            ) {
                return action.apply(context);
            }
        };
    }

    private AnalysisCapability findingCapability(
            String id,
            AnalysisOrigin origin,
            FindingType type
    ) {
        return capability(id, origin, context -> new CapabilityAnalysisResult(
                List.of(new IntelligenceFinding(
                        id + "-finding",
                        id,
                        origin,
                        type,
                        id,
                        id,
                        Map.of(),
                        new BigDecimal("0.5"),
                        Set.of(ContextSectionType.MARKET_SNAPSHOT),
                        Instant.now()
                )),
                List.of()
        ));
    }

    private List<ContextRequirement> publicBaseline() {
        return List.of(
                ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY),
                ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT)
        );
    }

    private ContextContributor identityContributor() {
        return contributor(
                ContextSectionType.MARKET_IDENTITY,
                ContextSensitivity.PUBLIC,
                new MarketIdentityContext(
                        UUID.randomUUID(), "KRAKEN", "BTC/USD", "BTC", "USD", true
                ),
                new AtomicInteger()
        );
    }

    private ContextContributor snapshotContributor() {
        Instant occurredAt = Instant.now();
        return contributor(
                ContextSectionType.MARKET_SNAPSHOT,
                ContextSensitivity.PUBLIC,
                new MarketSnapshotContext(
                        UUID.randomUUID(), "BTC/USD", new BigDecimal("100"),
                        new BigDecimal("99"), new BigDecimal("101"), true, occurredAt
                ),
                new AtomicInteger()
        );
    }

    private ContextContributor accountContributor(AtomicInteger calls) {
        return contributor(
                ContextSectionType.ACCOUNT,
                ContextSensitivity.USER_PRIVATE,
                null,
                calls
        );
    }

    private ContextContributor contributor(
            ContextSectionType type,
            ContextSensitivity sensitivity,
            ContextPayload payload,
            AtomicInteger calls
    ) {
        return new ContextContributor() {
            @Override
            public ContextSectionType sectionType() {
                return type;
            }

            @Override
            public ContextSection contribute(IntelligenceAnalysisRequest request) {
                calls.incrementAndGet();
                Instant now = Instant.now();
                return new ContextSection(
                        type,
                        ContextSectionStatus.AVAILABLE,
                        sensitivity,
                        payload,
                        new ContextProvenance("test", now, now),
                        null
                );
            }
        };
    }

    private IntelligenceAnalysisRequest request(AnalysisExecutionMode mode) {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), mode, "Understand the market"
        );
    }
}
