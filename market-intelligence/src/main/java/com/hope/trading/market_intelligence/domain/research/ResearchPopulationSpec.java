package com.hope.trading.market_intelligence.domain.research;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.*;

public record ResearchPopulationSpec(
        String populationPolicyVersion,
        Set<UUID> marketIds,
        Instant startCutoffInclusive,
        Instant endCutoffExclusive,
        String featureProjectionVersion,
        EvidenceAcquisitionPolicy featureEvidencePolicy,
        String outcomeDefinitionVersion,
        Set<OutcomeHorizon> horizons,
        ResearchSamplingCadence samplingCadence,
        Set<FeatureMissingness> acceptedFeatureMissingness,
        Map<ResearchOutcomeKey, String> selectedOutcomeFingerprints
) {
    public static final String FUTURE_INTERVAL = "FIFTEEN_MINUTES";

    public ResearchPopulationSpec {
        required(populationPolicyVersion, "population policy version");
        Objects.requireNonNull(marketIds, "market IDs");
        marketIds = Set.copyOf(marketIds);
        Objects.requireNonNull(startCutoffInclusive, "start cutoff");
        Objects.requireNonNull(endCutoffExclusive, "end cutoff");
        if (!startCutoffInclusive.isBefore(endCutoffExclusive)) {
            throw new IllegalArgumentException("start cutoff must precede end cutoff");
        }
        required(featureProjectionVersion, "feature projection version");
        Objects.requireNonNull(featureEvidencePolicy, "feature evidence policy");
        if (featureEvidencePolicy != EvidenceAcquisitionPolicy.STRICT_OBSERVED) {
            throw new IllegalArgumentException("research features require STRICT_OBSERVED evidence");
        }
        required(outcomeDefinitionVersion, "outcome definition version");
        Objects.requireNonNull(horizons, "horizons");
        if (horizons.isEmpty()) throw new IllegalArgumentException("at least one horizon is required");
        horizons = Set.copyOf(horizons);
        Objects.requireNonNull(samplingCadence, "sampling cadence");
        Objects.requireNonNull(acceptedFeatureMissingness, "accepted feature missingness");
        acceptedFeatureMissingness = Set.copyOf(acceptedFeatureMissingness);
        Objects.requireNonNull(selectedOutcomeFingerprints, "selected outcome fingerprints");
        selectedOutcomeFingerprints = Map.copyOf(selectedOutcomeFingerprints);
        selectedOutcomeFingerprints.forEach((key, value) -> {
            Objects.requireNonNull(key, "outcome selection key");
            required(value, "selected outcome fingerprint");
        });
    }

    public boolean includes(UUID marketId, Instant cutoff) {
        return (marketIds.isEmpty() || marketIds.contains(marketId))
                && !cutoff.isBefore(startCutoffInclusive)
                && cutoff.isBefore(endCutoffExclusive);
    }

    public Optional<String> selectedOutcomeFingerprint(ResearchOutcomeKey key) {
        return Optional.ofNullable(selectedOutcomeFingerprints.get(key));
    }

    public String interval() {
        return FUTURE_INTERVAL;
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
