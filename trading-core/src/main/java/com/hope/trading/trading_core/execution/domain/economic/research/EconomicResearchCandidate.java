package com.hope.trading.trading_core.execution.domain.economic.research;

import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;

import java.util.Objects;

public record EconomicResearchCandidate(
        EconomicResearchDatasetDefinition datasetDefinition,
        FeatureSnapshotReference featureSnapshot,
        StrategyHypothesisIdentity hypothesis,
        StrategyIntent intent,
        FixedBaseQuantityPolicy sizingPolicy,
        TimeHorizonExitPolicy exitPolicy,
        String executionModelVersion,
        String transactionCostModelVersion
) {
    public EconomicResearchCandidate {
        Objects.requireNonNull(datasetDefinition, "datasetDefinition is required");
        Objects.requireNonNull(featureSnapshot, "featureSnapshot is required");
        Objects.requireNonNull(hypothesis, "hypothesis is required");
        Objects.requireNonNull(intent, "intent is required");
        Objects.requireNonNull(sizingPolicy, "sizingPolicy is required");
        if (intent.orderType() != StrategyIntent.OrderType.MARKET) {
            throw new IllegalArgumentException("economic research V1 only supports MARKET intents");
        }
        if (intent.quantity().compareTo(sizingPolicy.baseQuantity()) != 0) {
            throw new IllegalArgumentException("intent quantity must match the fixed base quantity policy");
        }
        Objects.requireNonNull(exitPolicy, "exitPolicy is required");
        executionModelVersion = required(executionModelVersion, "executionModelVersion");
        transactionCostModelVersion = required(transactionCostModelVersion, "transactionCostModelVersion");
        if (!datasetDefinition.hypothesis().equals(hypothesis)
                || !datasetDefinition.sizingPolicy().equals(sizingPolicy)
                || !datasetDefinition.exitPolicy().equals(exitPolicy)
                || !datasetDefinition.executionModelVersion().equals(executionModelVersion)
                || !datasetDefinition.transactionCostModelVersion().equals(transactionCostModelVersion)
                || !datasetDefinition.featureProjectionVersion().equals(featureSnapshot.featureProjectionVersion())) {
            throw new IllegalArgumentException("candidate semantics must match its dataset definition");
        }
        if (!datasetDefinition.marketIds().contains(featureSnapshot.marketId())
                || featureSnapshot.evaluationCutoff().isBefore(datasetDefinition.startCutoffInclusive())
                || !featureSnapshot.evaluationCutoff().isBefore(datasetDefinition.endCutoffExclusive())) {
            throw new IllegalArgumentException("feature snapshot must belong to the dataset population");
        }
    }

    public String fingerprint() {
        return EconomicResearchFingerprint.of(datasetDefinition.fingerprint(), featureSnapshot.identityFingerprint(),
                hypothesis.fingerprint(), intent.instrument(), intent.side().name(), intent.orderType().name(),
                intent.quantity().toPlainString(), intent.decisionAt().toString(), sizingPolicy.fingerprint(),
                exitPolicy.fingerprint(), executionModelVersion, transactionCostModelVersion);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
