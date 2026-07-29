package com.hope.trading.market_intelligence.application.orchestration;

import com.hope.trading.market_intelligence.application.capability.*;
import com.hope.trading.market_intelligence.application.context.IntelligenceContextAssembler;
import com.hope.trading.market_intelligence.application.strategy.*;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IntelligenceOrchestrator {
    private final IntelligenceContextAssembler contextAssembler;
    private final AnalysisStrategyRegistry strategyRegistry;
    private final Map<String, AnalysisCapability> capabilities;
    private final IntelligenceConsolidator consolidator;
    private final ExecutorService executor;

    public IntelligenceOrchestrator(
            IntelligenceContextAssembler contextAssembler,
            AnalysisStrategyRegistry strategyRegistry,
            List<AnalysisCapability> capabilities,
            IntelligenceConsolidator consolidator,
            ExecutorService executor
    ) {
        this.contextAssembler = contextAssembler;
        this.strategyRegistry = strategyRegistry;
        this.capabilities = capabilities.stream().collect(Collectors.toUnmodifiableMap(
                AnalysisCapability::id,
                Function.identity()
        ));
        this.consolidator = consolidator;
        this.executor = executor;
    }

    public ConsolidatedIntelligence analyze(IntelligenceAnalysisRequest request) {
        Instant startedAt = Instant.now();
        AnalysisExecutionPlan plan = strategyRegistry.strategy(request.mode()).plan(request);
        List<AnalysisCapability> selected = plan.capabilityIds().stream()
                .limit(plan.maximumCapabilities())
                .map(capabilities::get)
                .filter(Objects::nonNull)
                .filter(capability -> capability.supportedModes().contains(request.mode()))
                .toList();
        List<ContextRequirement> requirements = mergeRequirements(
                plan.baselineContext(),
                selected.stream()
                        .filter(capability -> capability.availability() == CapabilityAvailability.AVAILABLE)
                        .flatMap(capability -> capability.requirements(request.mode()).stream())
                        .toList()
        );
        IntelligenceContext context = contextAssembler.assemble(request, requirements);
        List<CapabilityRunResult> runs = selected.stream()
                .map(capability -> execute(capability, request, context, plan.timeout()))
                .toList();

        return consolidator.consolidate(
                request, context, requirements, runs, startedAt, plan.timeout()
        );
    }

    private CapabilityRunResult execute(
            AnalysisCapability capability,
            IntelligenceAnalysisRequest request,
            IntelligenceContext context,
            Duration timeout
    ) {
        Instant startedAt = Instant.now();
        if (capability.availability() == CapabilityAvailability.UNAVAILABLE) {
            return run(
                    capability, CapabilityExecutionStatus.UNAVAILABLE,
                    startedAt, "Capability is not configured",
                    CapabilityAnalysisResult.empty("Capability unavailable: " + capability.id())
            );
        }

        List<ContextRequirement> requirements = capability.requirements(request.mode());
        if (!canExecute(context, requirements)) {
            return run(
                    capability, CapabilityExecutionStatus.SKIPPED,
                    startedAt, "Required context is missing or unavailable",
                    CapabilityAnalysisResult.empty("Capability skipped: " + capability.id())
            );
        }

        Set<ContextSectionType> allowedSections = requirements.stream()
                .map(ContextRequirement::sectionType)
                .collect(Collectors.toUnmodifiableSet());
        IntelligenceContext capabilityContext = context.select(allowedSections);
        Future<CapabilityAnalysisResult> future = executor.submit(
                () -> capability.analyze(request, capabilityContext)
        );
        try {
            CapabilityAnalysisResult result = future.get(
                    timeout.toMillis(), TimeUnit.MILLISECONDS
            );
            return run(
                    capability, CapabilityExecutionStatus.COMPLETED,
                    startedAt, null, result
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            return run(
                    capability, CapabilityExecutionStatus.TIMED_OUT,
                    startedAt, "Capability timed out",
                    CapabilityAnalysisResult.empty("Capability timed out: " + capability.id())
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return run(
                    capability, CapabilityExecutionStatus.FAILED,
                    startedAt, "Execution interrupted",
                    CapabilityAnalysisResult.empty("Capability interrupted: " + capability.id())
            );
        } catch (ExecutionException exception) {
            return run(
                    capability, CapabilityExecutionStatus.FAILED,
                    startedAt, "Capability execution failed",
                    CapabilityAnalysisResult.empty("Capability failed: " + capability.id())
            );
        }
    }

    private boolean canExecute(
            IntelligenceContext context,
            List<ContextRequirement> requirements
    ) {
        return requirements.stream()
                .filter(ContextRequirement::required)
                .allMatch(requirement -> context.section(requirement.sectionType())
                        .filter(section -> section.sensitivity() == requirement.sensitivity())
                        .filter(section -> section.status() == ContextSectionStatus.AVAILABLE
                                || section.status() == ContextSectionStatus.STALE)
                        .isPresent());
    }

    private CapabilityRunResult run(
            AnalysisCapability capability,
            CapabilityExecutionStatus status,
            Instant startedAt,
            String message,
            CapabilityAnalysisResult result
    ) {
        return new CapabilityRunResult(
                new CapabilityExecution(
                        capability.id(),
                        capability.origin(),
                        status,
                        Duration.between(startedAt, Instant.now()),
                        message
                ),
                result
        );
    }

    private List<ContextRequirement> mergeRequirements(
            List<ContextRequirement> first,
            List<ContextRequirement> second
    ) {
        Map<ContextSectionType, ContextRequirement> merged =
                new EnumMap<>(ContextSectionType.class);
        java.util.stream.Stream.concat(first.stream(), second.stream())
                .forEach(requirement -> merged.merge(
                        requirement.sectionType(),
                        requirement,
                        (left, right) -> new ContextRequirement(
                                left.sectionType(),
                                left.required() || right.required(),
                                left.sensitivity() == ContextSensitivity.USER_PRIVATE
                                        || right.sensitivity() == ContextSensitivity.USER_PRIVATE
                                        ? ContextSensitivity.USER_PRIVATE
                                        : ContextSensitivity.PUBLIC
                        )
                ));
        return List.copyOf(merged.values());
    }
}
