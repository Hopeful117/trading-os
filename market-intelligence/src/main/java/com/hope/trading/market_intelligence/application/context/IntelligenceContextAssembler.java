package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.Instant;

@Component
public class IntelligenceContextAssembler {
    private static final Logger log = LoggerFactory.getLogger(IntelligenceContextAssembler.class);
    private final Map<ContextSectionType, ContextContributor> contributors;
    private final Clock clock;

    public IntelligenceContextAssembler(List<ContextContributor> contributors) {
        this(contributors, Clock.systemUTC());
    }

    @Autowired
    public IntelligenceContextAssembler(List<ContextContributor> contributors, Clock clock) {
        this.contributors = contributors.stream().collect(Collectors.toUnmodifiableMap(
                ContextContributor::sectionType,
                Function.identity()
        ));
        this.clock = clock;
    }

    public IntelligenceContext assemble(
            IntelligenceAnalysisRequest request,
            List<ContextRequirement> requirements
    ) {
        if (request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED
                && request.evaluationCutoff() == null) {
            throw new IllegalArgumentException(
                    "Strict observed analysis requires an explicit evaluation cutoff");
        }
        Instant cutoff = request.evaluationCutoff() == null
                ? clock.instant()
                : request.evaluationCutoff();
        IntelligenceAnalysisRequest boundedRequest = request.withEvaluationCutoff(cutoff);
        Map<ContextSectionType, ContextSection> sections =
                new EnumMap<>(ContextSectionType.class);
        requirements.forEach(requirement ->
                sections.computeIfAbsent(
                        requirement.sectionType(),
                        ignored -> load(boundedRequest, requirement)
                )
        );
        ContextQuality quality = quality(requirements, sections);
        List<String> diagnostics = sections.values().stream()
                .filter(section -> section.status() != ContextSectionStatus.AVAILABLE)
                .map(section -> section.type() + ":" + section.status())
                .sorted()
                .toList();
        return new IntelligenceContext(
                boundedRequest.marketId(), boundedRequest.mode(), boundedRequest.objective(),
                cutoff, ContextFingerprintFactory.version(), quality, diagnostics,
                ContextFingerprintFactory.fingerprint(boundedRequest, requirements, sections),
                sections
        );
    }

    private ContextQuality quality(
            List<ContextRequirement> requirements,
            Map<ContextSectionType, ContextSection> sections
    ) {
        Map<ContextSectionType, Boolean> required = requirements.stream()
                .collect(Collectors.toMap(ContextRequirement::sectionType,
                        ContextRequirement::required, (left, right) -> left || right));
        if (sections.entrySet().stream()
                .anyMatch(entry -> required.getOrDefault(entry.getKey(), false)
                        && entry.getValue().status().blocksAnalysis())) {
            return ContextQuality.DEGRADED;
        }
        if (sections.values().stream().anyMatch(section -> section.status() != ContextSectionStatus.AVAILABLE)) {
            return ContextQuality.PARTIAL;
        }
        return ContextQuality.COMPLETE;
    }

    private ContextSection load(
            IntelligenceAnalysisRequest request,
            ContextRequirement requirement
    ) {
        ContextContributor contributor = contributors.get(requirement.sectionType());
        if (contributor == null) {
            return ContextSection.missing(
                    requirement,
                    "No contributor registered for " + requirement.sectionType()
            );
        }
        try {
            return contributor.contribute(request);
        } catch (RuntimeException exception) {
            log.warn("Context contribution failed analysis={} market={} section={} errorType={} message={}",
                    request.analysisId(), request.marketId(), requirement.sectionType(),
                    exception.getClass().getSimpleName(), exception.getMessage());
            return ContextSection.unavailable(
                    requirement,
                    "Contributor failed for " + requirement.sectionType()
            );
        }
    }
}
