package com.hope.trading.trading_core.execution.domain.economic.research;

import java.math.BigDecimal;
import java.util.*;

public record EconomicResearchDatasetManifest(
        String exportSchemaVersion,
        String datasetDefinitionFingerprint,
        int candidateCount,
        int resultCount,
        Map<String, Integer> executionStatusCounts,
        Map<String, Integer> costCompletenessCounts,
        Map<EconomicResearchResultDisposition, Integer> dispositionCounts,
        BigDecimal residualExposureTotal,
        int residualExposureCount,
        List<String> candidateFingerprints,
        List<String> resultFingerprints,
        String candidatesSha256,
        String resultsSha256,
        String sourceBundleFingerprint
) {
    public EconomicResearchDatasetManifest {
        exportSchemaVersion = required(exportSchemaVersion, "exportSchemaVersion");
        datasetDefinitionFingerprint = required(datasetDefinitionFingerprint, "datasetDefinitionFingerprint");
        if (candidateCount < 0 || resultCount < 0 || residualExposureCount < 0) {
            throw new IllegalArgumentException("manifest counts cannot be negative");
        }
        executionStatusCounts = counts(executionStatusCounts, "executionStatusCounts");
        costCompletenessCounts = counts(costCompletenessCounts, "costCompletenessCounts");
        dispositionCounts = enumCounts(dispositionCounts, "dispositionCounts");
        if (countValues(dispositionCounts) != resultCount) {
            throw new IllegalArgumentException("dispositionCounts must sum to resultCount");
        }
        if (countValues(executionStatusCounts) > resultCount) {
            throw new IllegalArgumentException("executionStatusCounts cannot exceed resultCount");
        }
        if (countValues(costCompletenessCounts) > resultCount) {
            throw new IllegalArgumentException("costCompletenessCounts cannot exceed resultCount");
        }
        Objects.requireNonNull(residualExposureTotal, "residualExposureTotal is required");
        if (residualExposureTotal.signum() < 0) throw new IllegalArgumentException("residualExposureTotal cannot be negative");
        if ((residualExposureCount == 0 && residualExposureTotal.signum() != 0)
                || (residualExposureCount > 0 && residualExposureTotal.signum() == 0)) {
            throw new IllegalArgumentException("residual exposure count and total must agree");
        }
        if (residualExposureCount > resultCount) {
            throw new IllegalArgumentException("residualExposureCount cannot exceed resultCount");
        }
        candidateFingerprints = fingerprints(candidateFingerprints, candidateCount, "candidateFingerprints");
        resultFingerprints = fingerprints(resultFingerprints, resultCount, "resultFingerprints");
        candidatesSha256 = required(candidatesSha256, "candidatesSha256");
        resultsSha256 = required(resultsSha256, "resultsSha256");
        sourceBundleFingerprint = required(sourceBundleFingerprint, "sourceBundleFingerprint");
    }

    public String fingerprint() {
        List<String> material = new ArrayList<>(List.of(exportSchemaVersion, datasetDefinitionFingerprint,
                Integer.toString(candidateCount), Integer.toString(resultCount)));
        executionStatusCounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.add("execution:" + entry.getKey() + "=" + entry.getValue()));
        costCompletenessCounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.add("cost:" + entry.getKey() + "=" + entry.getValue()));
        dispositionCounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.add("disposition:" + entry.getKey().name() + "=" + entry.getValue()));
        material.addAll(List.of(residualExposureTotal.toPlainString(), Integer.toString(residualExposureCount)));
        material.addAll(candidateFingerprints);
        material.addAll(resultFingerprints);
        material.addAll(List.of(candidatesSha256, resultsSha256, sourceBundleFingerprint));
        return EconomicResearchFingerprint.of(material);
    }

    private static Map<String, Integer> counts(Map<String, Integer> values, String field) {
        Objects.requireNonNull(values, field);
        Map<String, Integer> normalized = new TreeMap<>();
        values.forEach((key, value) -> {
            String name = required(key, field + " key");
            if (value == null || value < 0) throw new IllegalArgumentException(field + " values cannot be negative");
            normalized.put(name, value);
        });
        return Map.copyOf(normalized);
    }

    private static Map<EconomicResearchResultDisposition, Integer> enumCounts(
            Map<EconomicResearchResultDisposition, Integer> values, String field) {
        Objects.requireNonNull(values, field);
        EnumMap<EconomicResearchResultDisposition, Integer> normalized =
                new EnumMap<>(EconomicResearchResultDisposition.class);
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, field + " key");
            if (value == null || value < 0) throw new IllegalArgumentException(field + " values cannot be negative");
            normalized.put(key, value);
        });
        return Map.copyOf(normalized);
    }

    private static List<String> fingerprints(List<String> values, int expected, String field) {
        Objects.requireNonNull(values, field);
        if (values.size() != expected) throw new IllegalArgumentException(field + " size must match its count");
        List<String> normalized = values.stream().map(value -> required(value, field + " value")).sorted().toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException(field + " must not contain duplicates");
        }
        return normalized;
    }

    private static int countValues(Map<?, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
