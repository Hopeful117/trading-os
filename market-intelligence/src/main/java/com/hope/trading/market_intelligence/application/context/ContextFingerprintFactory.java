package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.ContextRequirement;
import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class ContextFingerprintFactory {
    private static final String CONTEXT_VERSION = "analysis-context-v1";

    private ContextFingerprintFactory() {
    }

    static String version() {
        return CONTEXT_VERSION;
    }

    static ArtifactFingerprint fingerprint(
            IntelligenceAnalysisRequest request,
            List<ContextRequirement> requirements,
            Map<?, ContextSection> sections
    ) {
        List<String> components = new ArrayList<>();
        components.add("schema=" + CONTEXT_VERSION);
        components.add("market=" + request.marketId());
        components.add("mode=" + request.mode());
        components.add("evidencePolicy=" + request.evidencePolicy());
        components.add("objective=" + request.objective());
        components.add("cutoff=" + request.evaluationCutoff());
        requirements.stream()
                .sorted(Comparator.comparing(requirement -> requirement.sectionType().name()))
                .map(requirement -> "requirement=" + requirement.sectionType()
                        + ":" + requirement.required() + ":" + requirement.sensitivity())
                .forEach(components::add);
        sections.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(entry -> "section=" + entry.getKey() + ":" + canonical(entry.getValue()))
                .forEach(components::add);
        return ArtifactFingerprint.ofInputs(components);
    }

    private static String canonical(ContextSection section) {
        if (section == null) {
            return "null";
        }
        return String.join("|",
                String.valueOf(section.type()),
                String.valueOf(section.status()),
                String.valueOf(section.sensitivity()),
                String.valueOf(section.payload()),
                String.valueOf(section.provenance()),
                String.valueOf(section.message()));
    }
}
