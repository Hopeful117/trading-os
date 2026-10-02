package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class IntelligenceContextAssembler {
    private static final Logger log = LoggerFactory.getLogger(IntelligenceContextAssembler.class);
    private final Map<ContextSectionType, ContextContributor> contributors;

    public IntelligenceContextAssembler(List<ContextContributor> contributors) {
        this.contributors = contributors.stream().collect(Collectors.toUnmodifiableMap(
                ContextContributor::sectionType,
                Function.identity()
        ));
    }

    public IntelligenceContext assemble(
            IntelligenceAnalysisRequest request,
            List<ContextRequirement> requirements
    ) {
        Map<ContextSectionType, ContextSection> sections =
                new EnumMap<>(ContextSectionType.class);
        requirements.forEach(requirement ->
                sections.computeIfAbsent(
                        requirement.sectionType(),
                        ignored -> load(request, requirement)
                )
        );
        return new IntelligenceContext(sections);
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
