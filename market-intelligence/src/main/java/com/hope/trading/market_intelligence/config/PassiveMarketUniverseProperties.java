package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@ConfigurationProperties(prefix = "market-intelligence.passive-universe")
public class PassiveMarketUniverseProperties {
    public static final String SUPPORTED_VERSION = "v1";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "KRAKEN", "CTRADER", "FTMO", "BINANCE", "COINBASE", "BYBIT");
    private static final Set<String> SUPPORTED_PRODUCT_TYPES = Set.of(
            "SPOT", "LINEAR_PERPETUAL", "CFD");
    private String version = SUPPORTED_VERSION;
    private List<Entry> entries = new ArrayList<>();

    @PostConstruct
    void validateAtStartup() {
        validate();
    }

    public void validate() {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new IllegalStateException("Unsupported passive market universe version: " + version);
        }
        if (entries == null) {
            throw new IllegalStateException("Passive market universe entries are required");
        }
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry == null) {
                throw new IllegalStateException("Passive market universe entry " + index + " is required");
            }
            if (entry.marketId == null || entry.provider == null || entry.provider.isBlank()
                    || entry.productType == null || entry.productType.isBlank()) {
                throw new IllegalStateException(
                        "Passive market universe entry " + index
                        + " requires market ID, provider and product type");
            }
            if (!SUPPORTED_PROVIDERS.contains(entry.provider)) {
                throw new IllegalStateException(
                        "Unsupported passive market universe provider: " + entry.provider);
            }
            if (!SUPPORTED_PRODUCT_TYPES.contains(entry.productType)) {
                throw new IllegalStateException(
                        "Unsupported passive market universe product type: " + entry.productType);
            }
            if (entry.enabled == null) {
                throw new IllegalStateException(
                        "Passive market universe entry " + index + " requires enabled");
            }
            if (entry.cadence == null || entry.cadence.isZero() || entry.cadence.isNegative()
                    || entry.cadence.compareTo(Duration.ofDays(30)) > 0) {
                throw new IllegalStateException(
                        "Passive market universe entry " + index + " requires a positive cadence up to 30 days");
            }
            if (entry.minimumEvidenceProfile == null) {
                throw new IllegalStateException(
                        "Passive market universe entry " + index + " requires an evidence profile");
            }
            if (!entry.provider.equals(entry.provider.strip()) || !entry.productType.equals(entry.productType.strip())) {
                throw new IllegalStateException(
                        "Passive market universe entry " + index + " contains padded identity values");
            }
        }
        validateConflictingIdentities();
    }

    private void validateConflictingIdentities() {
        Map<UUID, Set<String>> identitiesByMarket = new TreeMap<>();
        Set<String> configuredIdentities = new TreeSet<>();
        for (Entry entry : entries) {
            String identity = entry.marketId + "|" + entry.provider + "|" + entry.productType;
            if (!configuredIdentities.add(identity)) {
                throw new IllegalStateException("Duplicate passive market universe identity: " + identity);
            }
            identitiesByMarket.computeIfAbsent(entry.marketId, ignored -> new TreeSet<>())
                    .add(entry.provider + "|" + entry.productType);
        }
        identitiesByMarket.forEach((marketId, identities) -> {
            if (identities.size() > 1) {
                throw new IllegalStateException(
                        "Conflicting passive market universe identities for market "
                                + marketId + ": " + identities);
            }
        });
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static class Entry {
        private UUID marketId;
        private String provider;
        private String productType;
        private Boolean enabled;
        private Duration cadence;
        private TradeabilityEvidenceProfile minimumEvidenceProfile;

        public UUID getMarketId() {
            return marketId;
        }

        public void setMarketId(UUID marketId) {
            this.marketId = marketId;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getProductType() {
            return productType;
        }

        public void setProductType(String productType) {
            this.productType = productType;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getCadence() {
            return cadence;
        }

        public void setCadence(Duration cadence) {
            this.cadence = cadence;
        }

        public TradeabilityEvidenceProfile getMinimumEvidenceProfile() {
            return minimumEvidenceProfile;
        }

        public void setMinimumEvidenceProfile(TradeabilityEvidenceProfile minimumEvidenceProfile) {
            this.minimumEvidenceProfile = minimumEvidenceProfile;
        }
    }
}
