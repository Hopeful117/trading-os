package com.hope.trading.market_intelligence.application.orchestration;

import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class IntelligenceConsolidator {
    public ConsolidatedIntelligence consolidate(
            IntelligenceAnalysisRequest request,
            IntelligenceContext context,
            List<ContextRequirement> requirements,
            List<CapabilityRunResult> runs,
            Instant startedAt,
            Duration timeout
    ) {
        List<IntelligenceFinding> findings = runs.stream()
                .flatMap(run -> run.result().findings().stream())
                .sorted(Comparator.comparing(IntelligenceFinding::generatedAt))
                .toList();
        List<String> warnings = new ArrayList<>();
        runs.forEach(run -> warnings.addAll(run.result().warnings()));

        List<ContextSectionSummary> sectionSummaries = context.sections().values().stream()
                .sorted(Comparator.comparing(section -> section.type().name()))
                .map(this::summary)
                .toList();
        requirements.stream()
                .filter(ContextRequirement::required)
                .map(requirement -> context.section(requirement.sectionType()).orElse(null))
                .filter(section -> section == null
                        || section.status() == ContextSectionStatus.MISSING
                        || section.status() == ContextSectionStatus.UNAVAILABLE)
                .forEach(section -> warnings.add(
                        section == null ? "Required context missing" : section.message()
                ));

        Instant completedAt = Instant.now();
        List<CapabilityExecution> executions = runs.stream()
                .map(CapabilityRunResult::execution)
                .toList();
        IntelligenceExecutionStatus status = status(context, requirements, executions, findings);
        AnalysisExecutionMetadata metadata = new AnalysisExecutionMetadata(
                request.analysisId(),
                request.mode(),
                startedAt,
                completedAt,
                Duration.between(startedAt, completedAt),
                timeout,
                executions
        );

        return new ConsolidatedIntelligence(
                request.analysisId(),
                request.marketId(),
                request.mode(),
                status,
                sectionSummaries,
                findings,
                warnings,
                metadata
        );
    }

    private IntelligenceExecutionStatus status(
            IntelligenceContext context,
            List<ContextRequirement> requirements,
            List<CapabilityExecution> executions,
            List<IntelligenceFinding> findings
    ) {
        boolean requiredUnavailable = requirements.stream()
                .filter(ContextRequirement::required)
                .map(requirement -> context.section(requirement.sectionType()).orElse(null))
                .anyMatch(section -> section == null
                        || section.status() == ContextSectionStatus.MISSING
                        || section.status() == ContextSectionStatus.UNAVAILABLE);
        boolean stale = context.sections().values().stream()
                .anyMatch(section -> section.status() == ContextSectionStatus.STALE);
        boolean optionalUnavailable = requirements.stream()
                .filter(requirement -> !requirement.required())
                .map(requirement -> context.section(requirement.sectionType()).orElse(null))
                .anyMatch(section -> section == null
                        || section.status() == ContextSectionStatus.MISSING
                        || section.status() == ContextSectionStatus.UNAVAILABLE);
        boolean capabilityIssue = executions.stream()
                .anyMatch(execution -> execution.status() != CapabilityExecutionStatus.COMPLETED);
        boolean completed = executions.stream()
                .anyMatch(execution -> execution.status() == CapabilityExecutionStatus.COMPLETED);

        boolean everySectionUnavailable = !context.sections().isEmpty()
                && context.sections().values().stream()
                    .allMatch(section -> section.status() == ContextSectionStatus.MISSING
                            || section.status() == ContextSectionStatus.UNAVAILABLE);
        if (!completed && findings.isEmpty() && everySectionUnavailable) {
            return IntelligenceExecutionStatus.FAILED;
        }
        if (requiredUnavailable || stale) {
            return IntelligenceExecutionStatus.DEGRADED;
        }
        return capabilityIssue || optionalUnavailable
                ? IntelligenceExecutionStatus.PARTIAL
                : IntelligenceExecutionStatus.COMPLETE;
    }

    private ContextSectionSummary summary(ContextSection section) {
        ContextProvenance provenance = section.provenance();
        return new ContextSectionSummary(
                section.type(),
                section.status(),
                section.sensitivity(),
                provenance == null ? null : provenance.source(),
                provenance == null ? null : provenance.sourceOccurredAt(),
                section.message()
        );
    }
}
