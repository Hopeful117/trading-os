package com.hope.trading.market_intelligence.application.research.histdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Explicit, immutable scientific boundary for a single ML-01 partition run. */
public record Ml01DukascopyExperimentConfiguration(
        String experiment,
        String version,
        String sourceDatasetFingerprint,
        String featureSchemaVersion,
        String outcomeDefinitionVersion,
        String dependencyPolicyId,
        String qualityProfile,
        List<String> featureNames,
        List<Integer> horizons,
        int kMeansNInit,
        String kMeansAlgorithm,
        int kMeansMaxIterations,
        String kMeansTolerance,
        String scenarioName,
        Instant startInclusive,
        Instant trainEndExclusive,
        Instant validationEndExclusive,
        Instant testEndExclusive,
        Duration embargo,
        List<Integer> kValues,
        List<Integer> seeds,
        String dependencyVersions,
        String uncertaintyMethod,
        Integer blockLength,
        String selectionThresholds,
        boolean scientificApproval) {

    public Ml01DukascopyExperimentConfiguration {
        if (experiment == null || version == null || sourceDatasetFingerprint == null || featureSchemaVersion == null
                || outcomeDefinitionVersion == null || dependencyPolicyId == null || qualityProfile == null
                || featureNames == null || horizons == null || kMeansAlgorithm == null || kMeansTolerance == null
                || scenarioName == null || startInclusive == null
                || trainEndExclusive == null || validationEndExclusive == null || testEndExclusive == null
                || embargo == null || kValues == null || seeds == null || dependencyVersions == null) {
            throw new IllegalArgumentException("ML-01 configuration is incomplete");
        }
        if (!List.of("COMMON_UTC_70_15_15", "COMMON_UTC_60_20_20").contains(scenarioName)
                || !startInclusive.isBefore(trainEndExclusive)
                || !trainEndExclusive.isBefore(validationEndExclusive)
                || !validationEndExclusive.isBefore(testEndExclusive)
                || embargo.isNegative() || kValues.isEmpty() || seeds.isEmpty() || featureNames.isEmpty()
                || horizons.isEmpty() || kMeansNInit < 1 || kMeansMaxIterations < 1) {
            throw new IllegalArgumentException("ML-01 configuration boundaries are contradictory");
        }
        if (scientificApproval && (uncertaintyMethod == null || blockLength == null || blockLength < 1
                || selectionThresholds == null || selectionThresholds.isBlank())) {
            throw new IllegalArgumentException("scientific approval requires explicit selection gates");
        }
        kValues = List.copyOf(kValues);
        seeds = List.copyOf(seeds);
        featureNames = List.copyOf(featureNames);
        horizons = List.copyOf(horizons);
    }

    public Ml01DukascopyPartitioning.Scenario scenario() {
        return new Ml01DukascopyPartitioning.Scenario(scenarioName, trainEndExclusive,
                validationEndExclusive, testEndExclusive);
    }

    public String fingerprint() {
        return ArtifactFingerprint.ofInputs(List.of(experiment, version, sourceDatasetFingerprint,
                featureSchemaVersion, outcomeDefinitionVersion, dependencyPolicyId, qualityProfile,
                featureNames.toString(), horizons.toString(), Integer.toString(kMeansNInit), kMeansAlgorithm,
                Integer.toString(kMeansMaxIterations), kMeansTolerance, scenarioName,
                startInclusive.toString(), trainEndExclusive.toString(), validationEndExclusive.toString(),
                testEndExclusive.toString(), embargo.toString(), kValues.toString(), seeds.toString(),
                dependencyVersions, String.valueOf(uncertaintyMethod), String.valueOf(blockLength),
                String.valueOf(selectionThresholds), Boolean.toString(scientificApproval))).value();
    }

    public boolean scientificallyReady() {
        return scientificApproval && uncertaintyMethod != null && blockLength != null && blockLength > 0
                && selectionThresholds != null && !selectionThresholds.isBlank();
    }

    public static Ml01DukascopyExperimentConfiguration of(String scenarioName, Instant startInclusive,
                                                          Instant endExclusive, Duration embargo, String qualityProfile,
                                                          String sourceDatasetFingerprint) {
        Duration duration = Duration.between(startInclusive, endExclusive);
        boolean sixty = switch (scenarioName) {
            case "COMMON_UTC_60_20_20" -> true;
            case "COMMON_UTC_70_15_15" -> false;
            default -> throw new IllegalArgumentException("unsupported ML-01 scenario: " + scenarioName);
        };
        Instant trainEnd = startInclusive.plusMillis(duration.toMillis() * (sixty ? 60 : 70) / 100);
        Instant validationEnd = trainEnd.plusMillis(duration.toMillis()
                * (sixty ? 20 : 15) / 100);
        return new Ml01DukascopyExperimentConfiguration("ML01_DUKASCOPY_REGIME_EVALUATION", "V1",
                sourceDatasetFingerprint, Ml01DukascopyPreparation.FEATURE_SCHEMA_VERSION,
                Ml01DukascopyPreparation.OUTCOME_DEFINITION_VERSION,
                Ml01DukascopyPartitioning.POLICY_ID, qualityProfile, Ml01DukascopyPreparation.FEATURES,
                Ml01DukascopyPreparation.HORIZONS, 10, "lloyd", 300, "1e-4", scenarioName,
                startInclusive, trainEnd, validationEnd,
                endExclusive, embargo, List.of(2, 3, 4, 5, 6), List.of(0, 1, 2),
                "java=" + Runtime.version().feature(), null, null, null, false);
    }
}
