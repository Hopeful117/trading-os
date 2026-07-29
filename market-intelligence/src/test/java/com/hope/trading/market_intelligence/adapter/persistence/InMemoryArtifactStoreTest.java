package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.artifact.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryArtifactStoreTest {
    @Test
    void savesInvalidatesWithoutDeletingAndEvictsSeparately() {
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactCacheKey key = ArtifactTestFixtures.key("v1", UUID.randomUUID());
        StoredArtifact artifact = ArtifactTestFixtures.artifact(
                key,
                ArtifactFreshness.validUntil(
                        ArtifactTestFixtures.NOW.minusSeconds(1),
                        ArtifactTestFixtures.NOW.plusSeconds(60),
                        "source-v1"
                )
        );

        store.save(artifact);
        assertThat(store.find(key)).contains(artifact);

        store.invalidate(key, ArtifactTestFixtures.NOW);
        assertThat(store.find(key))
                .get()
                .extracting(value -> value.freshness().invalidatedAt())
                .isEqualTo(ArtifactTestFixtures.NOW);

        store.evict(key);
        assertThat(store.find(key)).isEmpty();
    }
}
