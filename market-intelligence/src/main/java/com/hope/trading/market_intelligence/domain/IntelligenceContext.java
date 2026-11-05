package com.hope.trading.market_intelligence.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IntelligenceContext {
    private final Map<ContextSectionType, ContextSection> sections;
    private final UUID marketId;
    private final AnalysisExecutionMode mode;
    private final String objective;
    private final Instant evaluationCutoff;
    private final String schemaVersion;
    private final ContextQuality quality;
    private final List<String> diagnostics;
    private final ArtifactFingerprint fingerprint;

    public IntelligenceContext(Map<ContextSectionType, ContextSection> sections) {
        this(null, null, null, null, "legacy-v1", legacyQuality(sections),
                legacyDiagnostics(sections), legacyFingerprint(sections), sections);
    }

    @SuppressWarnings("java:S107")
    public IntelligenceContext(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            Instant evaluationCutoff,
            String schemaVersion,
            ContextQuality quality,
            List<String> diagnostics,
            ArtifactFingerprint fingerprint,
            Map<ContextSectionType, ContextSection> sections
    ) {
        Map<ContextSectionType, ContextSection> copy =
                new EnumMap<>(ContextSectionType.class);
        copy.putAll(sections);
        this.sections = Map.copyOf(copy);
        this.marketId = marketId;
        this.mode = mode;
        this.objective = objective;
        this.evaluationCutoff = evaluationCutoff;
        this.schemaVersion = schemaVersion;
        this.quality = quality;
        this.diagnostics = List.copyOf(diagnostics);
        this.fingerprint = fingerprint;
    }

    public Optional<ContextSection> section(ContextSectionType type) {
        return Optional.ofNullable(sections.get(type));
    }

    @JsonIgnore
    public Map<ContextSectionType, ContextSection> sections() {
        return sections;
    }

    @JsonProperty("sections")
    public Map<ContextSectionType, ContextSection> publicSections() {
        return sections.entrySet().stream()
                .filter(entry -> entry.getValue().sensitivity() == ContextSensitivity.PUBLIC)
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }

    @JsonProperty
    public Optional<UUID> marketId() {
        return Optional.ofNullable(marketId);
    }

    @JsonProperty
    public Optional<AnalysisExecutionMode> mode() {
        return Optional.ofNullable(mode);
    }

    @JsonProperty
    public Optional<String> objective() {
        return Optional.ofNullable(objective);
    }

    @JsonProperty
    public Optional<Instant> evaluationCutoff() {
        return Optional.ofNullable(evaluationCutoff);
    }

    @JsonProperty
    public String schemaVersion() {
        return schemaVersion;
    }

    @JsonProperty
    public ContextQuality quality() {
        return quality;
    }

    @JsonProperty
    public List<String> diagnostics() {
        return diagnostics;
    }

    @JsonProperty
    public ArtifactFingerprint fingerprint() {
        return fingerprint;
    }

    public IntelligenceContext select(Set<ContextSectionType> allowedSections) {
        Map<ContextSectionType, ContextSection> selected =
                new EnumMap<>(ContextSectionType.class);
        allowedSections.forEach(type -> {
            ContextSection section = sections.get(type);
            if (section != null) {
                selected.put(type, section);
            }
        });
        ContextQuality selectedQuality;
        if (selected.values().stream().anyMatch(section -> section.status().blocksAnalysis())) {
            selectedQuality = ContextQuality.DEGRADED;
        } else if (selected.values().stream()
                .anyMatch(section -> section.status() == ContextSectionStatus.STALE)) {
            selectedQuality = ContextQuality.PARTIAL;
        } else {
            selectedQuality = ContextQuality.COMPLETE;
        }
        List<String> selectedDiagnostics = selected.values().stream()
                .filter(section -> section.status() != ContextSectionStatus.AVAILABLE)
                .map(section -> section.type() + ":" + section.status())
                .sorted()
                .toList();
        ArtifactFingerprint selectedFingerprint = ArtifactFingerprint.ofInputs(List.of(
                fingerprint.value(),
                "selected=" + selected.keySet().stream().map(Enum::name).sorted().toList()
        ));
        return new IntelligenceContext(
                marketId, mode, objective, evaluationCutoff, schemaVersion, selectedQuality,
                selectedDiagnostics, selectedFingerprint, selected);
    }

    private static ArtifactFingerprint legacyFingerprint(
            Map<ContextSectionType, ContextSection> sections
    ) {
        return ArtifactFingerprint.ofInputs(sections.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .toList());
    }

    private static ContextQuality legacyQuality(Map<ContextSectionType, ContextSection> sections) {
        if (sections.values().stream().anyMatch(section -> section.status().blocksAnalysis())) {
            return ContextQuality.DEGRADED;
        }
        return sections.values().stream().anyMatch(section -> section.status() != ContextSectionStatus.AVAILABLE)
                ? ContextQuality.PARTIAL : ContextQuality.COMPLETE;
    }

    private static List<String> legacyDiagnostics(Map<ContextSectionType, ContextSection> sections) {
        return sections.values().stream()
                .filter(section -> section.status() != ContextSectionStatus.AVAILABLE)
                .map(section -> section.type() + ":" + section.status())
                .sorted()
                .toList();
    }
}
