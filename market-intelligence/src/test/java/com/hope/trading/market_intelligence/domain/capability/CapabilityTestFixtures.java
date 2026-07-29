package com.hope.trading.market_intelligence.domain.capability;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;

import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class CapabilityTestFixtures {
    public static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    public static final ArtifactType INPUT = new ArtifactType("INPUT");
    public static final ArtifactType INTERMEDIATE = new ArtifactType("INTERMEDIATE");
    public static final ArtifactType OUTPUT = new ArtifactType("OUTPUT");
    public static final ArtifactVersion V1 = new ArtifactVersion("v1");
    public static final ArtifactVersion V2 = new ArtifactVersion("v2");

    public static Capability capability(
            String id, List<ArtifactRequirement> requirements,
            List<ProducedContribution> produced,
            RetryPolicy retry, Function<CapabilityContext, CapabilityResult> behavior) {
        CapabilityMetadata metadata = new CapabilityMetadata(
                new CapabilityId(id), new CapabilityVersion("v1"),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.REQUIRED,
                retry, requirements, produced, Duration.ofSeconds(2), null);
        return new TestCapability(metadata, behavior);
    }

    public static ArtifactRequirement requirement(
            ArtifactType type, ArtifactVersion version) {
        return new ArtifactRequirement(
                type, version, VersionCompatibilityMode.EXACT,
                true, ArtifactCardinality.ONE, false);
    }

    public static ProducedContribution.ArtifactContribution produces(
            ArtifactType type, ArtifactVersion version) {
        return new ProducedContribution.ArtifactContribution(type, version, Set.of());
    }

    public static CapabilityResult result(ArtifactType type, ArtifactVersion version) {
        ProducedArtifact artifact = new ProducedArtifact(type, version, stored(type, version));
        return new CapabilityResult(
                List.of(produces(type, version)), List.of(artifact),
                Map.of(), List.of(), CapabilityCompleteness.COMPLETE);
    }

    public static StoredArtifact stored(ArtifactType type, ArtifactVersion version) {
        UUID market = UUID.randomUUID();
        ArtifactCacheKey key = new ArtifactCacheKey(
                new ArtifactIdentity(type.value(), "test", version.value()),
                ArtifactScope.publicMarket(market, null, AnalysisExecutionMode.PASSIVE),
                ArtifactFingerprint.empty(), ArtifactFingerprint.empty());
        return new StoredArtifact(
                key, new TestContent(type.value()),
                ArtifactFreshness.validUntil(NOW, NOW.plusSeconds(60), "source-v1"),
                new ArtifactProvenance("test", version.value(), UUID.randomUUID(),
                        NOW, Set.of(), Set.of()),
                AnalysisResultQuality.COMPLETE);
    }

    public record TestContent(String value) implements ArtifactContent {}
    private record TestCapability(
            CapabilityMetadata metadata,
            Function<CapabilityContext, CapabilityResult> behavior) implements Capability {
        @Override public CapabilityResult execute(CapabilityContext context) {
            return behavior.apply(context);
        }
    }
    private CapabilityTestFixtures() {}
}
