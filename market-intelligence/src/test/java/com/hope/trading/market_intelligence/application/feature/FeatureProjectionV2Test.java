package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.domain.feature.FeatureDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureProjectionV2Test {
    @Test
    void extendsV1SchemaWithDeterministicVolatilityFeatures() {
        var v1 = FeatureProjectionV1.schema();
        var v2 = FeatureProjectionV2.schema();

        assertThat(v2.version()).isEqualTo(FeatureProjectionV2.VERSION);
        assertThat(v2.definitions()).extracting(FeatureDefinition::order)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, v2.definitions().size())
                        .boxed().toList());
        assertThat(v2.definitions().subList(0, v1.definitions().size()))
                .extracting(FeatureDefinition::name)
                .containsExactlyElementsOf(v1.definitions().stream().map(FeatureDefinition::name).toList());
        assertThat(v2.definitions()).extracting(FeatureDefinition::name)
                .contains("ohlc.atr", "ohlc.normalized_atr", "ohlc.realized_volatility",
                        "ohlc.volatility_compression_ratio", "ohlc.directional_efficiency");
    }
}
