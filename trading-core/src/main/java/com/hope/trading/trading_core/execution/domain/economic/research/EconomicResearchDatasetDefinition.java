package com.hope.trading.trading_core.execution.domain.economic.research;

import com.hope.trading.trading_core.execution.domain.economic.CostCompleteness;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;

import java.time.Instant;
import java.util.*;

public record EconomicResearchDatasetDefinition(
        String definitionVersion,
        String populationPolicyVersion,
        List<UUID> marketIds,
        Instant startCutoffInclusive,
        Instant endCutoffExclusive,
        String featureProjectionVersion,
        String evidencePolicyVersion,
        StrategyHypothesisIdentity hypothesis,
        FixedBaseQuantityPolicy sizingPolicy,
        String entryPolicyVersion,
        TimeHorizonExitPolicy exitPolicy,
        String executionModelVersion,
        String transactionCostModelVersion,
        String researchCutoffPolicyVersion,
        CostCompleteness requiredCostCompleteness,
        Set<SimulatedExecutionStatus> acceptedExecutionStatuses,
        String objectiveOutcomeJoinPolicyVersion
) {
    public EconomicResearchDatasetDefinition {
        definitionVersion = required(definitionVersion, "definitionVersion");
        populationPolicyVersion = required(populationPolicyVersion, "populationPolicyVersion");
        marketIds = normalizeMarkets(marketIds);
        Objects.requireNonNull(startCutoffInclusive, "startCutoffInclusive is required");
        Objects.requireNonNull(endCutoffExclusive, "endCutoffExclusive is required");
        if (!startCutoffInclusive.isBefore(endCutoffExclusive)) {
            throw new IllegalArgumentException("startCutoffInclusive must be before endCutoffExclusive");
        }
        featureProjectionVersion = required(featureProjectionVersion, "featureProjectionVersion");
        evidencePolicyVersion = required(evidencePolicyVersion, "evidencePolicyVersion");
        Objects.requireNonNull(hypothesis, "hypothesis is required");
        Objects.requireNonNull(sizingPolicy, "sizingPolicy is required");
        entryPolicyVersion = required(entryPolicyVersion, "entryPolicyVersion");
        Objects.requireNonNull(exitPolicy, "exitPolicy is required");
        executionModelVersion = required(executionModelVersion, "executionModelVersion");
        transactionCostModelVersion = required(transactionCostModelVersion, "transactionCostModelVersion");
        researchCutoffPolicyVersion = required(researchCutoffPolicyVersion, "researchCutoffPolicyVersion");
        Objects.requireNonNull(requiredCostCompleteness, "requiredCostCompleteness is required");
        acceptedExecutionStatuses = Set.copyOf(Objects.requireNonNull(acceptedExecutionStatuses,
                "acceptedExecutionStatuses is required"));
        if (acceptedExecutionStatuses.isEmpty()) {
            throw new IllegalArgumentException("acceptedExecutionStatuses must not be empty");
        }
        objectiveOutcomeJoinPolicyVersion = optional(objectiveOutcomeJoinPolicyVersion);
    }

    public String fingerprint() {
        List<String> material = new ArrayList<>(List.of(definitionVersion, populationPolicyVersion));
        material.addAll(marketIds.stream().map(UUID::toString).sorted().toList());
        material.addAll(List.of(startCutoffInclusive.toString(), endCutoffExclusive.toString(),
                featureProjectionVersion, evidencePolicyVersion, hypothesis.fingerprint(), sizingPolicy.fingerprint(),
                entryPolicyVersion, exitPolicy.fingerprint(), executionModelVersion, transactionCostModelVersion,
                researchCutoffPolicyVersion, requiredCostCompleteness.name()));
        material.addAll(acceptedExecutionStatuses.stream().map(Enum::name).sorted().toList());
        material.add(objectiveOutcomeJoinPolicyVersion);
        return EconomicResearchFingerprint.of(material);
    }

    private static List<UUID> normalizeMarkets(List<UUID> values) {
        Objects.requireNonNull(values, "marketIds is required");
        List<UUID> normalized = values.stream().map(value -> Objects.requireNonNull(value, "marketId is required"))
                .sorted().toList();
        if (normalized.isEmpty()) throw new IllegalArgumentException("marketIds must not be empty");
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException("marketIds must not contain duplicates");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
