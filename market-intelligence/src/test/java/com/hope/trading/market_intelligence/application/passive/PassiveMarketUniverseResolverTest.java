package com.hope.trading.market_intelligence.application.passive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.config.PassiveMarketUniverseProperties;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PassiveMarketUniverseResolverTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void resolvesExactIdentityInDeterministicOrder() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(market(SECOND, "KRAKEN", "SPOT"),
                market(FIRST, "CTRADER", "CFD")));
        PassiveMarketUniverseProperties properties = properties(
                entry(SECOND, "KRAKEN", "SPOT", true), entry(FIRST, "CTRADER", "CFD", true));

        PassiveMarketUniverse result = resolver(properties, client).resolve();

        assertThat(result.entries()).extracting(PassiveMarketEntry::marketId)
                .containsExactly(FIRST, SECOND);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.enabled()).isTrue();
        assertThat(result.fingerprint()).hasSize(64);
        verify(client).findAllMarkets();
    }

    @Test
    void equivalentConfigurationOrderProducesSameFingerprint() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(
                market(FIRST, "CTRADER", "CFD"), market(SECOND, "KRAKEN", "SPOT")));
        PassiveMarketUniverse first = resolver(properties(
                entry(FIRST, "CTRADER", "CFD", true), entry(SECOND, "KRAKEN", "SPOT", true)), client).resolve();
        PassiveMarketUniverse second = resolver(properties(
                entry(SECOND, "KRAKEN", "SPOT", true), entry(FIRST, "CTRADER", "CFD", true)), client).resolve();

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(first.resolvedAt()).isEqualTo(NOW);
    }

    @Test
    void reportsUnknownAndConflictingIdentityWithoutGuessing() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(market(FIRST, "CTRADER", "CFD")));
        PassiveMarketUniverse result = resolver(properties(
                entry(SECOND, "KRAKEN", "SPOT", true), entry(FIRST, "KRAKEN", "SPOT", true)), client).resolve();

        assertThat(result.entries()).isEmpty();
        assertThat(result.diagnostics()).extracting(PassiveMarketUniverseDiagnostic::code)
                .containsExactlyInAnyOrder(
                        PassiveMarketUniverseDiagnostic.Code.UNKNOWN_MARKET,
                        PassiveMarketUniverseDiagnostic.Code.PROVIDER_MISMATCH);
    }

    @Test
    void rejectsConflictingConfiguredIdentitiesForOneMarket() {
        PassiveMarketUniverseProperties properties = properties(
                entry(FIRST, "CTRADER", "CFD", true), entry(FIRST, "KRAKEN", "SPOT", true));

        assertThatThrownBy(() -> resolver(properties, mock(MarketDataClient.class)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting passive market universe identities");
    }

    @Test
    void reportsProductTypeMismatch() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(market(FIRST, "CTRADER", "CFD")));

        PassiveMarketUniverse result = resolver(properties(
                entry(FIRST, "CTRADER", "SPOT", true)), client).resolve();

        assertThat(result.entries()).isEmpty();
        assertThat(result.diagnostics()).extracting(PassiveMarketUniverseDiagnostic::code)
                .containsExactly(PassiveMarketUniverseDiagnostic.Code.PRODUCT_TYPE_MISMATCH);
    }

    @Test
    void reportsConflictingCatalogueIdentitiesForOneMarket() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(
                market(FIRST, "CTRADER", "CFD"), market(FIRST, "CTRADER", "SPOT")));

        PassiveMarketUniverse result = resolver(properties(
                entry(FIRST, "CTRADER", "CFD", true)), client).resolve();

        assertThat(result.entries()).isEmpty();
        assertThat(result.diagnostics()).extracting(PassiveMarketUniverseDiagnostic::code)
                .containsExactly(PassiveMarketUniverseDiagnostic.Code.CATALOGUE_IDENTITY_CONFLICT);
    }

    @Test
    void reportsDisabledEntryAndDoesNotResolveIt() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(market(FIRST, "CTRADER", "CFD")));

        PassiveMarketUniverse result = resolver(properties(
                entry(FIRST, "CTRADER", "CFD", false)), client).resolve();

        assertThat(result.entries()).isEmpty();
        assertThat(result.diagnostics()).extracting(PassiveMarketUniverseDiagnostic::code)
                .containsExactly(PassiveMarketUniverseDiagnostic.Code.DISABLED_ENTRY);
        verifyNoInteractions(client);
    }

    @Test
    void fingerprintChangesWhenSelectionChangesAndIgnoresResolutionTime() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(
                market(FIRST, "CTRADER", "CFD"), market(SECOND, "KRAKEN", "SPOT")));
        PassiveMarketUniverseProperties firstProperties = properties(
                entry(FIRST, "CTRADER", "CFD", true));
        PassiveMarketUniverseProperties.Entry changed = entry(SECOND, "KRAKEN", "SPOT", true);
        changed.setMinimumEvidenceProfile(TradeabilityEvidenceProfile.LEVEL_II_REQUIRED);

        PassiveMarketUniverse first = resolver(firstProperties, client).resolve();
        PassiveMarketUniverse later = new PassiveMarketUniverseResolver(
                properties(changed), client, new ObjectMapper(),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)).resolve();
        PassiveMarketUniverse sameSelectionLater = new PassiveMarketUniverseResolver(
                firstProperties, client, new ObjectMapper(),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)).resolve();

        assertThat(first.fingerprint()).isNotEqualTo(later.fingerprint());
        assertThat(first.fingerprint()).isEqualTo(sameSelectionLater.fingerprint());

        PassiveMarketUniverseProperties.Entry cadenceChanged = entry(FIRST, "CTRADER", "CFD", true);
        cadenceChanged.setCadence(Duration.ofMinutes(10));
        PassiveMarketUniverse cadenceChangedUniverse = resolver(properties(cadenceChanged), client).resolve();
        assertThat(first.fingerprint()).isNotEqualTo(cadenceChangedUniverse.fingerprint());

        PassiveMarketUniverse versionChangedUniverse = new PassiveMarketUniverseResolver(
                versionedProperties("test-version"), client, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)).resolve();
        assertThat(first.fingerprint()).isNotEqualTo(versionChangedUniverse.fingerprint());
    }

    @Test
    void reportsCatalogueUnavailable() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenThrow(new IllegalStateException("unavailable"));

        PassiveMarketUniverse result = resolver(properties(
                entry(FIRST, "CTRADER", "CFD", true)), client).resolve();

        assertThat(result.entries()).isEmpty();
        assertThat(result.diagnostics()).extracting(PassiveMarketUniverseDiagnostic::code)
                .containsExactly(PassiveMarketUniverseDiagnostic.Code.CATALOGUE_UNAVAILABLE);
    }

    @Test
    void rejectsDuplicateConfiguredIdentity() {
        MarketDataClient client = mock(MarketDataClient.class);
        PassiveMarketUniverseProperties properties = properties(
                entry(FIRST, "CTRADER", "CFD", true), entry(FIRST, "CTRADER", "CFD", true));

        assertThatThrownBy(() -> resolver(properties, client).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate passive market universe identity");
    }

    @Test
    void returnedCollectionsAreImmutable() {
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findAllMarkets()).thenReturn(List.of(market(FIRST, "CTRADER", "CFD")));
        PassiveMarketUniverse result = resolver(properties(
                entry(FIRST, "CTRADER", "CFD", true)), client).resolve();

        assertThatThrownBy(() -> result.entries().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.diagnostics().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private PassiveMarketUniverseResolver resolver(
            PassiveMarketUniverseProperties properties, MarketDataClient client) {
        return new PassiveMarketUniverseResolver(properties, client, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PassiveMarketUniverseProperties properties(PassiveMarketUniverseProperties.Entry... entries) {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties();
        properties.setEntries(List.of(entries));
        return properties;
    }

    private PassiveMarketUniverseProperties versionedProperties(String version) {
        PassiveMarketUniverseProperties properties = new PassiveMarketUniverseProperties() {
            @Override
            public String getVersion() {
                return version;
            }

            @Override
            public void validate() {
                // The production validator rejects unsupported versions; this isolates fingerprint input coverage.
            }
        };
        properties.setEntries(List.of(entry(FIRST, "CTRADER", "CFD", true)));
        return properties;
    }

    private PassiveMarketUniverseProperties.Entry entry(
            UUID marketId, String provider, String productType, boolean enabled) {
        PassiveMarketUniverseProperties.Entry entry = new PassiveMarketUniverseProperties.Entry();
        entry.setMarketId(marketId);
        entry.setProvider(provider);
        entry.setProductType(productType);
        entry.setEnabled(enabled);
        entry.setCadence(Duration.ofMinutes(5));
        entry.setMinimumEvidenceProfile(TradeabilityEvidenceProfile.FOREX_QUOTE_BASIC);
        return entry;
    }

    private MarketResponse market(UUID marketId, String provider, String productType) {
        return new MarketResponse(marketId, provider, "EUR/USD", "EUR", "USD", productType, null);
    }
}
