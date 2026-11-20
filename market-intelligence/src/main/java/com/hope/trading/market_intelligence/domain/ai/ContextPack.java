package com.hope.trading.market_intelligence.domain.ai;

import com.hope.trading.market_intelligence.domain.ContextQuality;
import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Immutable, Market Intelligence-owned view authorized for one AI interpretation. */
public final class ContextPack {
    public static final String SCHEMA_VERSION = "context-pack-v1";

    private final ContextPackId id;
    private final String schemaVersion;
    private final AiInterpretationMode mode;
    private final ContextPackAuthorization authorization;
    private final UUID marketId;
    private final OpportunityVersionReference opportunity;
    private final MarketThesisReference thesis;
    private final Instant evaluationCutoff;
    private final ContextQuality quality;
    private final List<String> diagnostics;
    private final Map<ContextSectionType, ContextSection> sections;
    private final Set<UUID> authorizedEvidenceReferences;
    private final ArtifactFingerprint fingerprint;

    private ContextPack(
            ContextPackId id,
            String schemaVersion,
            AiInterpretationMode mode,
            ContextPackAuthorization authorization,
            UUID marketId,
            OpportunityVersionReference opportunity,
            MarketThesisReference thesis,
            Instant evaluationCutoff,
            ContextQuality quality,
            List<String> diagnostics,
            Map<ContextSectionType, ContextSection> sections,
            Set<UUID> authorizedEvidenceReferences,
            ArtifactFingerprint fingerprint
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.schemaVersion = required(schemaVersion, "schemaVersion");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.marketId = Objects.requireNonNull(marketId, "marketId");
        this.opportunity = Objects.requireNonNull(opportunity, "opportunity");
        this.thesis = thesis;
        this.evaluationCutoff = Objects.requireNonNull(evaluationCutoff, "evaluationCutoff");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        this.sections = Map.copyOf(Objects.requireNonNull(sections, "sections"));
        this.authorizedEvidenceReferences = Set.copyOf(
                Objects.requireNonNull(authorizedEvidenceReferences, "authorizedEvidenceReferences"));
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.sections.forEach((type, section) -> {
            Objects.requireNonNull(type, "section type");
            Objects.requireNonNull(section, "section");
            if (section.type() != type) {
                throw new IllegalArgumentException("Section key must match section type");
            }
            if (section.provenance() != null
                    && ((section.provenance().sourceOccurredAt() != null
                    && section.provenance().sourceOccurredAt().isAfter(evaluationCutoff))
                    || (section.provenance().evaluationCutoff() != null
                    && section.provenance().evaluationCutoff().isAfter(evaluationCutoff)))) {
                throw new IllegalArgumentException("Context evidence cannot follow evaluation cutoff");
            }
        });
    }

    public static ContextPack from(
            ContextPackId id,
            AiInterpretationMode mode,
            ContextPackAuthorization authorization,
            IntelligenceContext context,
            OpportunityVersionReference opportunity,
            MarketThesisReference thesis
    ) {
        Objects.requireNonNull(context, "context");
        ContextPack provisional = new ContextPack(
                id,
                SCHEMA_VERSION,
                mode,
                authorization,
                context.marketId().orElseThrow(() -> new IllegalArgumentException("Context market is required")),
                opportunity,
                thesis,
                context.evaluationCutoff().orElseThrow(
                        () -> new IllegalArgumentException("Context evaluation cutoff is required")),
                context.quality(),
                context.diagnostics(),
                context.publicSections(),
                evidenceReferences(context.publicSections()),
                ArtifactFingerprint.empty()
        );
        return new ContextPack(
                provisional.id, provisional.schemaVersion, provisional.mode, provisional.authorization,
                provisional.marketId,
                provisional.opportunity, provisional.thesis, provisional.evaluationCutoff,
                provisional.quality, provisional.diagnostics, provisional.sections,
                provisional.authorizedEvidenceReferences,
                ContextPackFingerprintFactory.fingerprint(provisional));
    }

    public ContextPackId id() { return id; }
    public String schemaVersion() { return schemaVersion; }
    public AiInterpretationMode mode() { return mode; }
    public ContextPackAuthorization authorization() { return authorization; }
    public UUID marketId() { return marketId; }
    public OpportunityVersionReference opportunity() { return opportunity; }
    public MarketThesisReference thesis() { return thesis; }
    public Instant evaluationCutoff() { return evaluationCutoff; }
    public ContextQuality quality() { return quality; }
    public List<String> diagnostics() { return diagnostics; }
    public Map<ContextSectionType, ContextSection> sections() { return sections; }
    public Set<UUID> authorizedEvidenceReferences() { return authorizedEvidenceReferences; }
    public ArtifactFingerprint fingerprint() { return fingerprint; }

    public boolean contains(ContextSectionType type) {
        return sections.containsKey(type);
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static Set<UUID> evidenceReferences(Map<ContextSectionType, ContextSection> sections) {
        return sections.values().stream()
                .map(ContextSection::provenance)
                .filter(Objects::nonNull)
                .map(provenance -> provenance.sourceReference())
                .filter(Objects::nonNull)
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ContextPack::parseUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
