package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassiveMarketUniversePropertiesTest {
    @Test
    void emptyConfigurationIsValidAndDisabled() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();

        properties.validate();
    }

    @Test
    void rejectsEntryWithoutRequiredIdentity() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        properties.setEntries(List.of(entry(null, "KRAKEN", "SPOT", true)));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("market ID");
    }

    @Test
    void rejectsMissingEnabledFlag() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        PassiveMarketUniverseProperties.Entry entry = entry(UUID.randomUUID(), "KRAKEN", "SPOT", true);
        entry.setEnabled(null);
        properties.setEntries(List.of(entry));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires enabled");
    }

    @Test
    void rejectsCadenceOutsideSupportedBounds() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        PassiveMarketUniverseProperties.Entry entry = entry(UUID.randomUUID(), "KRAKEN", "SPOT", true);
        entry.setCadence(Duration.ofDays(31));
        properties.setEntries(List.of(entry));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cadence");
    }

    @Test
    void rejectsUnsupportedProvider() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        properties.setEntries(List.of(entry(UUID.randomUUID(), "UNKNOWN", "SPOT", true)));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported passive market universe provider");
    }

    @Test
    void rejectsUnsupportedProductType() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        properties.setEntries(List.of(entry(UUID.randomUUID(), "KRAKEN", "UNKNOWN", true)));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported passive market universe product type");
    }

    @Test
    void rejectsUnsupportedVersion() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        properties.setVersion("v2");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported passive market universe version");
    }

    @Test
    void rejectsMissingEvidenceProfile() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        PassiveMarketUniverseProperties.Entry entry = entry(UUID.randomUUID(), "KRAKEN", "SPOT", true);
        entry.setMinimumEvidenceProfile(null);
        properties.setEntries(List.of(entry));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an evidence profile");
    }

    @Test
    void rejectsNonPositiveCadence() {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        PassiveMarketUniverseProperties.Entry entry = entry(UUID.randomUUID(), "KRAKEN", "SPOT", true);
        entry.setCadence(Duration.ZERO);
        properties.setEntries(List.of(entry));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cadence");
    }

    @Test
    void invalidBoundConfigurationFailsDuringSpringStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues(
                        "market-intelligence.passive-universe.entries[0].market-id="
                                + "00000000-0000-0000-0000-000000000001",
                        "market-intelligence.passive-universe.entries[0].provider=UNKNOWN",
                        "market-intelligence.passive-universe.entries[0].product-type=SPOT",
                        "market-intelligence.passive-universe.entries[0].enabled=true",
                        "market-intelligence.passive-universe.entries[0].cadence=5m",
                        "market-intelligence.passive-universe.entries[0].minimum-evidence-profile=UNSUPPORTED")
                .run(context -> {
                    if (context.getStartupFailure() == null) {
                        throw new AssertionError("Invalid passive universe configuration should fail startup");
                    }
        });
    }

    @Test
    void conflictingBoundIdentitiesFailDuringSpringStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues(
                        "market-intelligence.passive-universe.entries[0].market-id="
                                + "00000000-0000-0000-0000-000000000001",
                        "market-intelligence.passive-universe.entries[0].provider=KRAKEN",
                        "market-intelligence.passive-universe.entries[0].product-type=SPOT",
                        "market-intelligence.passive-universe.entries[0].enabled=true",
                        "market-intelligence.passive-universe.entries[0].cadence=5m",
                        "market-intelligence.passive-universe.entries[0].minimum-evidence-profile=UNSUPPORTED",
                        "market-intelligence.passive-universe.entries[1].market-id="
                                + "00000000-0000-0000-0000-000000000001",
                        "market-intelligence.passive-universe.entries[1].provider=CTRADER",
                        "market-intelligence.passive-universe.entries[1].product-type=CFD",
                        "market-intelligence.passive-universe.entries[1].enabled=true",
                        "market-intelligence.passive-universe.entries[1].cadence=5m",
                        "market-intelligence.passive-universe.entries[1].minimum-evidence-profile=UNSUPPORTED")
                .run(context -> {
                    if (context.getStartupFailure() == null) {
                        throw new AssertionError("Conflicting passive identities should fail startup");
                    }
        });
    }

    @Test
    void duplicateBoundIdentityFailsDuringSpringStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues(
                        "market-intelligence.passive-universe.entries[0].market-id="
                                + "00000000-0000-0000-0000-000000000001",
                        "market-intelligence.passive-universe.entries[0].provider=KRAKEN",
                        "market-intelligence.passive-universe.entries[0].product-type=SPOT",
                        "market-intelligence.passive-universe.entries[0].enabled=true",
                        "market-intelligence.passive-universe.entries[0].cadence=5m",
                        "market-intelligence.passive-universe.entries[0].minimum-evidence-profile=UNSUPPORTED",
                        "market-intelligence.passive-universe.entries[1].market-id="
                                + "00000000-0000-0000-0000-000000000001",
                        "market-intelligence.passive-universe.entries[1].provider=KRAKEN",
                        "market-intelligence.passive-universe.entries[1].product-type=SPOT",
                        "market-intelligence.passive-universe.entries[1].enabled=true",
                        "market-intelligence.passive-universe.entries[1].cadence=5m",
                        "market-intelligence.passive-universe.entries[1].minimum-evidence-profile=UNSUPPORTED")
                .run(context -> {
                    if (context.getStartupFailure() == null) {
                        throw new AssertionError("Duplicate passive identities should fail startup");
                    }
                });
    }

    @Test
    void validBoundConfigurationCreatesPropertiesBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues(
                        "market-intelligence.passive-universe.entries[0].market-id="
                                + "00000000-0000-0000-0000-000000000001",
                        "market-intelligence.passive-universe.entries[0].provider=KRAKEN",
                        "market-intelligence.passive-universe.entries[0].product-type=SPOT",
                        "market-intelligence.passive-universe.entries[0].enabled=true",
                        "market-intelligence.passive-universe.entries[0].cadence=5m",
                        "market-intelligence.passive-universe.entries[0].minimum-evidence-profile=UNSUPPORTED")
                .run(context -> {
                    if (context.getStartupFailure() != null
                            || context.getBean(PassiveMarketUniverseProperties.class).getEntries().size() != 1) {
                        throw new AssertionError("Valid passive universe configuration should bind successfully");
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PassiveMarketUniverseProperties.class)
    static class BindingConfiguration {
    }

    private PassiveMarketUniverseProperties.Entry entry(
            UUID marketId, String provider, String productType, boolean enabled) {
        PassiveMarketUniverseProperties.Entry entry = new PassiveMarketUniverseProperties.Entry();
        entry.setMarketId(marketId);
        entry.setProvider(provider);
        entry.setProductType(productType);
        entry.setEnabled(enabled);
        entry.setCadence(Duration.ofMinutes(5));
        entry.setMinimumEvidenceProfile(TradeabilityEvidenceProfile.UNSUPPORTED);
        return entry;
    }
}
