package com.hope.trading.market_intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ConfigurationProperties(prefix = "research.lifecycle")
public class ResearchLifecycleProperties {
    private boolean enabled;
    private List<UUID> marketIds = new ArrayList<>();
    private Duration observationCadence = Duration.ofHours(1);
    private List<Duration> outcomeHorizons = new ArrayList<>(List.of(
            Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(24)));
    private Duration fixedDelay = Duration.ofMinutes(1);
    private Duration readinessCacheDuration = Duration.ofSeconds(30);
    private int readinessRemoteConcurrency = 8;
    private Duration readinessRemoteTimeout = Duration.ofSeconds(20);
    private int maxRemoteEvidenceChecks = 512;
    private boolean artifactPopulationEnabled;
    private int artifactPopulationDepth = 25;
    private int artifactPopulationMaxPerRun = 1;
    private String artifactPopulationPromotionReason = "research-lifecycle:v1";
    private S1FeeCoverage feeCoverage = new S1FeeCoverage();

    public void validate() {
        if (!enabled) {
            return;
        }
        if (marketIds.isEmpty()) {
            throw new IllegalStateException("Research lifecycle requires at least one market ID when enabled");
        }
        if (observationCadence.isZero() || observationCadence.isNegative()) {
            throw new IllegalStateException("Research observation cadence must be positive");
        }
        if (fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalStateException("Research lifecycle fixed delay must be positive");
        }
        if (readinessCacheDuration.isZero() || readinessCacheDuration.isNegative()) {
            throw new IllegalStateException("Research readiness cache duration must be positive");
        }
        if (readinessRemoteConcurrency < 1) {
            throw new IllegalStateException("Research readiness remote concurrency must be positive");
        }
        if (readinessRemoteTimeout.isZero() || readinessRemoteTimeout.isNegative()) {
            throw new IllegalStateException("Research readiness remote timeout must be positive");
        }
        if (maxRemoteEvidenceChecks < 1) {
            throw new IllegalStateException("Research readiness remote evidence budget must be positive");
        }
        if (artifactPopulationEnabled && artifactPopulationDepth < 1) {
            throw new IllegalStateException("Research artifact population depth must be positive");
        }
        if (artifactPopulationEnabled && artifactPopulationMaxPerRun < 1) {
            throw new IllegalStateException("Research artifact population limit must be positive");
        }
        if (artifactPopulationEnabled && (artifactPopulationPromotionReason == null
                || artifactPopulationPromotionReason.isBlank())) {
            throw new IllegalStateException("Research artifact population promotion reason is required");
        }
        if (outcomeHorizons.isEmpty() || outcomeHorizons.stream().anyMatch(Duration::isNegative)
                || outcomeHorizons.stream().anyMatch(Duration::isZero)) {
            throw new IllegalStateException("Research outcome horizons must be positive");
        }
        if (new LinkedHashSet<>(outcomeHorizons).size() != outcomeHorizons.size()) {
            throw new IllegalStateException("Research outcome horizons must be unique");
        }
        feeCoverage.validate();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<UUID> getMarketIds() {
        return Set.copyOf(new LinkedHashSet<>(marketIds));
    }

    public void setMarketIds(List<UUID> marketIds) {
        this.marketIds = marketIds == null ? new ArrayList<>() : new ArrayList<>(marketIds);
    }

    public Duration getObservationCadence() {
        return observationCadence;
    }

    public void setObservationCadence(Duration observationCadence) {
        this.observationCadence = observationCadence;
    }

    public List<Duration> getOutcomeHorizons() {
        return List.copyOf(outcomeHorizons);
    }

    public void setOutcomeHorizons(List<Duration> outcomeHorizons) {
        this.outcomeHorizons = outcomeHorizons == null ? new ArrayList<>() : new ArrayList<>(outcomeHorizons);
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public Duration getReadinessCacheDuration() {
        return readinessCacheDuration;
    }

    public void setReadinessCacheDuration(Duration readinessCacheDuration) {
        this.readinessCacheDuration = readinessCacheDuration;
    }

    public int getReadinessRemoteConcurrency() {
        return readinessRemoteConcurrency;
    }

    public void setReadinessRemoteConcurrency(int readinessRemoteConcurrency) {
        this.readinessRemoteConcurrency = readinessRemoteConcurrency;
    }

    public Duration getReadinessRemoteTimeout() {
        return readinessRemoteTimeout;
    }

    public void setReadinessRemoteTimeout(Duration readinessRemoteTimeout) {
        this.readinessRemoteTimeout = readinessRemoteTimeout;
    }

    public int getMaxRemoteEvidenceChecks() {
        return maxRemoteEvidenceChecks;
    }

    public void setMaxRemoteEvidenceChecks(int maxRemoteEvidenceChecks) {
        this.maxRemoteEvidenceChecks = maxRemoteEvidenceChecks;
    }

    public boolean isArtifactPopulationEnabled() {
        return artifactPopulationEnabled;
    }

    public void setArtifactPopulationEnabled(boolean artifactPopulationEnabled) {
        this.artifactPopulationEnabled = artifactPopulationEnabled;
    }

    public int getArtifactPopulationDepth() {
        return artifactPopulationDepth;
    }

    public void setArtifactPopulationDepth(int artifactPopulationDepth) {
        this.artifactPopulationDepth = artifactPopulationDepth;
    }

    public int getArtifactPopulationMaxPerRun() {
        return artifactPopulationMaxPerRun;
    }

    public void setArtifactPopulationMaxPerRun(int artifactPopulationMaxPerRun) {
        this.artifactPopulationMaxPerRun = artifactPopulationMaxPerRun;
    }

    public String getArtifactPopulationPromotionReason() {
        return artifactPopulationPromotionReason;
    }

    public void setArtifactPopulationPromotionReason(String artifactPopulationPromotionReason) {
        this.artifactPopulationPromotionReason = artifactPopulationPromotionReason;
    }

    public S1FeeCoverage getFeeCoverage() {
        return feeCoverage;
    }

    public void setFeeCoverage(S1FeeCoverage feeCoverage) {
        this.feeCoverage = feeCoverage == null ? new S1FeeCoverage() : feeCoverage;
    }

    public static class S1FeeCoverage {
        private UUID brokerAccountId;
        private String provider;
        private String productType;
        private String instrument;

        public void validate() {
            if (brokerAccountId == null && blank(provider) && blank(productType) && blank(instrument)) {
                return;
            }
            if (brokerAccountId == null || blank(provider) || blank(productType) || blank(instrument)) {
                throw new IllegalStateException(
                        "S1 fee coverage requires brokerAccountId, provider, productType and instrument");
            }
        }

        public UUID getBrokerAccountId() { return brokerAccountId; }
        public void setBrokerAccountId(UUID brokerAccountId) { this.brokerAccountId = brokerAccountId; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public String getInstrument() { return instrument; }
        public void setInstrument(String instrument) { this.instrument = instrument; }

        private static boolean blank(String value) { return value == null || value.isBlank(); }
    }
}
