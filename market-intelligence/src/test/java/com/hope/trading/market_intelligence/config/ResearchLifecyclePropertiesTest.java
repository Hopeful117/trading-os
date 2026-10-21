package com.hope.trading.market_intelligence.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchLifecyclePropertiesTest {
    @Test
    void disabledLifecycleDoesNotRequireMarkets() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();

        properties.validate();
    }

    @Test
    void enabledLifecycleRejectsMissingMarkets() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("market ID");
    }

    @Test
    void rejectsDuplicateHorizons() {
        ResearchLifecycleProperties properties = valid();
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1), Duration.ofHours(1)));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void rejectsNonPositiveCadence() {
        ResearchLifecycleProperties properties = valid();
        properties.setObservationCadence(Duration.ZERO);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cadence");
    }

    @Test
    void rejectsNonPositiveReadinessRemoteTimeout() {
        ResearchLifecycleProperties properties = valid();
        properties.setReadinessRemoteTimeout(Duration.ZERO);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remote timeout");
    }

    @Test
    void rejectsPartialS1FeeIdentity() {
        ResearchLifecycleProperties properties = valid();
        properties.getFeeCoverage().setProvider("KRAKEN");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fee coverage requires");
    }

    @Test
    void acceptsEmptyS1FeeIdentityFromEnvironment() {
        ResearchLifecycleProperties properties = valid();
        properties.getFeeCoverage().setProvider("");
        properties.getFeeCoverage().setProductType("");
        properties.getFeeCoverage().setInstrument("");

        properties.validate();
    }

    @Test
    void rejectsNonPositiveArtifactPopulationDepthWhenEnabled() {
        ResearchLifecycleProperties properties = valid();
        properties.setArtifactPopulationEnabled(true);
        properties.setArtifactPopulationDepth(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("population depth");
    }

    @Test
    void rejectsBlankArtifactPromotionReasonWhenEnabled() {
        ResearchLifecycleProperties properties = valid();
        properties.setArtifactPopulationEnabled(true);
        properties.setArtifactPopulationPromotionReason(" ");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("promotion reason");
    }

    private ResearchLifecycleProperties valid() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(UUID.randomUUID()));
        return properties;
    }
}
