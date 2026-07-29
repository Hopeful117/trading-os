package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AiArtifactIdentityTest {
    @Test
    void contextModelPromptInferenceToolsAndSecurityParticipateInIdentity() {
        ArtifactScope scope = ArtifactScope.publicMarket(
                UUID.randomUUID(), "1h", AnalysisExecutionMode.ACTIVE
        );
        AiArtifactIdentity baseline = identity("context-a", "model-class-a");
        AiArtifactIdentity changedContext = identity("context-b", "model-class-a");
        AiArtifactIdentity changedModel = identity("context-a", "model-class-b");

        assertThat(baseline.toCacheKey(scope))
                .isNotEqualTo(changedContext.toCacheKey(scope))
                .isNotEqualTo(changedModel.toCacheKey(scope));
    }

    private AiArtifactIdentity identity(String context, String modelClass) {
        return new AiArtifactIdentity(
                "scenario-analysis",
                "v2",
                ArtifactFingerprint.ofInputs(List.of(context)),
                "prompt-v3",
                modelClass,
                ArtifactFingerprint.ofParameters(Map.of("temperature", "low")),
                ArtifactFingerprint.ofInputs(List.of("news-read")),
                ContextClassification.PUBLIC
        );
    }
}
