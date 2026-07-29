package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ArtifactCacheKeyTest {
    private final UUID marketId = UUID.randomUUID();

    @Test
    void parameterOrderDoesNotChangeTheKey() {
        ArtifactFingerprint first = ArtifactFingerprint.ofParameters(
                new LinkedHashMap<>(Map.of("period", 20, "depth", 10))
        );
        Map<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("depth", 10);
        reversed.put("period", 20);
        ArtifactFingerprint second = ArtifactFingerprint.ofParameters(reversed);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void significantParameterProducerVersionAndInputChangeTheKey() {
        ArtifactCacheKey baseline = ArtifactTestFixtures.key("v1", marketId);
        ArtifactCacheKey changedParameter = new ArtifactCacheKey(
                baseline.identity(),
                baseline.scope(),
                ArtifactFingerprint.ofParameters(Map.of("depth", 20, "period", 20)),
                baseline.inputFingerprint()
        );
        ArtifactCacheKey changedInput = new ArtifactCacheKey(
                baseline.identity(),
                baseline.scope(),
                baseline.parametersFingerprint(),
                ArtifactFingerprint.ofInputs(List.of("snapshot:43"))
        );

        assertThat(ArtifactTestFixtures.key("v1", marketId)).isEqualTo(baseline);
        assertThat(ArtifactTestFixtures.key("v2", marketId)).isNotEqualTo(baseline);
        assertThat(changedParameter).isNotEqualTo(baseline);
        assertThat(changedInput).isNotEqualTo(baseline);
    }

    @Test
    void privateScopesAreStrictlyIsolatedWhilePublicScopeHasNoUserDimension() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        ArtifactScope privateA =
                ArtifactTestFixtures.privateScope(marketId, userA, accountA);
        ArtifactScope privateB =
                ArtifactTestFixtures.privateScope(marketId, userB, accountA);
        ArtifactScope anotherAccount =
                ArtifactTestFixtures.privateScope(
                        marketId, userA, UUID.randomUUID()
                );

        assertThat(privateA).isNotEqualTo(privateB);
        assertThat(privateA).isNotEqualTo(anotherAccount);
        assertThat(ArtifactScope.publicMarket(
                marketId, "5m", AnalysisExecutionMode.PASSIVE
        )).isEqualTo(ArtifactScope.publicMarket(
                marketId, "5m", AnalysisExecutionMode.PASSIVE
        ));
        assertThatThrownBy(() -> new ArtifactScope(
                marketId, null, null, null, null, null,
                AnalysisExecutionMode.PASSIVE,
                ContextClassification.TRADING_SENSITIVE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idempotencyAndArtifactKeysRemainDifferentTypes() {
        assertThat(ArtifactCacheKey.class)
                .isNotEqualTo(com.hope.trading.market_intelligence.domain.execution
                        .IdempotencyKey.class);
    }
}
