package com.hope.trading.market_intelligence.application.artifact;

import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactInvalidationServiceTest {
    @Test
    void propagatesOnlyToRegisteredDependentsAndPreservesEntries() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        InMemoryArtifactDependencyRegistry dependencies =
                new InMemoryArtifactDependencyRegistry();
        UUID marketId = UUID.randomUUID();
        ArtifactCacheKey source = ArtifactTestFixtures.key("source", marketId);
        ArtifactCacheKey dependent = ArtifactTestFixtures.key("dependent", marketId);
        ArtifactCacheKey unrelated = ArtifactTestFixtures.key("unrelated", marketId);
        store.save(artifact(source));
        store.save(artifact(dependent));
        store.save(artifact(unrelated));
        dependencies.register(new ArtifactDependency(source, dependent));
        ArtifactInvalidationService service = new ArtifactInvalidationService(
                store,
                dependencies,
                Clock.fixed(ArtifactTestFixtures.NOW, ZoneOffset.UTC)
        );

        var invalidated = service.invalidateWithDependents(source);

        assertThat(invalidated).containsExactlyInAnyOrder(source, dependent);
        assertThat(store.find(source).orElseThrow().freshness().invalidatedAt())
                .isEqualTo(ArtifactTestFixtures.NOW);
        assertThat(store.find(dependent).orElseThrow().freshness().invalidatedAt())
                .isEqualTo(ArtifactTestFixtures.NOW);
        assertThat(store.find(unrelated).orElseThrow().freshness().invalidatedAt())
                .isNull();
    }

    private StoredArtifact artifact(ArtifactCacheKey key) {
        return ArtifactTestFixtures.artifact(
                key,
                ArtifactFreshness.validUntil(
                        ArtifactTestFixtures.NOW.minusSeconds(1),
                        ArtifactTestFixtures.NOW.plusSeconds(60),
                        "source-v1"
                )
        );
    }
}
