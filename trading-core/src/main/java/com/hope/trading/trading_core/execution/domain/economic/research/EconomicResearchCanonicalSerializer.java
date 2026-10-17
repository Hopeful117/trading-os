package com.hope.trading.trading_core.execution.domain.economic.research;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hope.trading.trading_core.execution.domain.economic.CostEvidence;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;
import com.hope.trading.trading_core.execution.domain.economic.ExplicitCost;
import com.hope.trading.trading_core.execution.domain.economic.ReferenceMarketState;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecution;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EconomicResearchCanonicalSerializer {
    private static final String STATUS = "status";
    private static final String REASON = "reason";
    private static final String QUANTITY = "quantity";
    private static final String POLICY_VERSION = "policyVersion";
    private static final String EXECUTION_MODEL_VERSION = "executionModelVersion";
    private static final String TRANSACTION_COST_MODEL_VERSION = "transactionCostModelVersion";
    private final ObjectMapper mapper;

    public EconomicResearchCanonicalSerializer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "object mapper").copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public String candidate(EconomicResearchCandidate candidate) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", "economic-research-candidate-v1");
        document.put("datasetDefinition", datasetDefinition(candidate.datasetDefinition()));
        document.put("datasetDefinitionFingerprint", candidate.datasetDefinition().fingerprint());
        document.put("featureSnapshot", featureSnapshot(candidate.featureSnapshot()));
        document.put("hypothesis", hypothesis(candidate.hypothesis()));
        document.put("intent", intent(candidate.intent()));
        document.put("sizingPolicy", sizing(candidate.sizingPolicy()));
        document.put("exitPolicy", exit(candidate.exitPolicy()));
        document.put(EXECUTION_MODEL_VERSION, candidate.executionModelVersion());
        document.put(TRANSACTION_COST_MODEL_VERSION, candidate.transactionCostModelVersion());
        document.put("candidateFingerprint", candidate.fingerprint());
        return write(document, "economic research candidate");
    }

    private Map<String, Object> datasetDefinition(EconomicResearchDatasetDefinition value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definitionVersion", value.definitionVersion());
        result.put("populationPolicyVersion", value.populationPolicyVersion());
        result.put("marketIds", value.marketIds());
        result.put("startCutoffInclusive", value.startCutoffInclusive().toString());
        result.put("endCutoffExclusive", value.endCutoffExclusive().toString());
        result.put("featureProjectionVersion", value.featureProjectionVersion());
        result.put("evidencePolicyVersion", value.evidencePolicyVersion());
        result.put("hypothesis", hypothesis(value.hypothesis()));
        result.put("sizingPolicy", sizing(value.sizingPolicy()));
        result.put("entryPolicyVersion", value.entryPolicyVersion());
        result.put("exitPolicy", exit(value.exitPolicy()));
        result.put(EXECUTION_MODEL_VERSION, value.executionModelVersion());
        result.put(TRANSACTION_COST_MODEL_VERSION, value.transactionCostModelVersion());
        result.put("researchCutoffPolicyVersion", value.researchCutoffPolicyVersion());
        result.put("requiredCostCompleteness", value.requiredCostCompleteness().name());
        result.put("acceptedExecutionStatuses", value.acceptedExecutionStatuses().stream().map(Enum::name).sorted().toList());
        result.put("objectiveOutcomeJoinPolicyVersion", value.objectiveOutcomeJoinPolicyVersion());
        return result;
    }

    public String result(EconomicResearchResult result) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("candidateFingerprint", result.candidateFingerprint());
        document.put("objectiveOutcomeFingerprint", result.objectiveOutcomeFingerprint());
        document.put("disposition", result.disposition().name());
        document.put(REASON, result.reason());
        document.put("economicResult", result.economicResult() == null ? null : economicResult(result.economicResult()));
        document.put("resultFingerprint", result.resultFingerprint());
        return write(document, "economic research result");
    }

    public String manifest(EconomicResearchDatasetManifest manifest) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("exportSchemaVersion", manifest.exportSchemaVersion());
        document.put("datasetDefinitionFingerprint", manifest.datasetDefinitionFingerprint());
        document.put("candidateCount", manifest.candidateCount());
        document.put("resultCount", manifest.resultCount());
        document.put("executionStatusCounts", sortedCounts(manifest.executionStatusCounts()));
        document.put("costCompletenessCounts", sortedCounts(manifest.costCompletenessCounts()));
        Map<String, Integer> dispositions = new LinkedHashMap<>();
        manifest.dispositionCounts().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> dispositions.put(entry.getKey().name(), entry.getValue()));
        document.put("dispositionCounts", dispositions);
        document.put("residualExposureTotal", manifest.residualExposureTotal());
        document.put("residualExposureCount", manifest.residualExposureCount());
        document.put("candidateFingerprints", manifest.candidateFingerprints());
        document.put("resultFingerprints", manifest.resultFingerprints());
        document.put("candidatesSha256", manifest.candidatesSha256());
        document.put("resultsSha256", manifest.resultsSha256());
        document.put("sourceBundleFingerprint", manifest.sourceBundleFingerprint());
        document.put("manifestFingerprint", manifest.fingerprint());
        return write(document, "economic research manifest");
    }

    private Map<String, Object> featureSnapshot(FeatureSnapshotReference reference) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("marketId", reference.marketId());
        value.put("evaluationCutoff", reference.evaluationCutoff().toString());
        value.put("featureProjectionVersion", reference.featureProjectionVersion());
        value.put("contextFingerprint", reference.contextFingerprint());
        value.put("featureSnapshotFingerprint", reference.featureSnapshotFingerprint());
        value.put("identityFingerprint", reference.identityFingerprint());
        return value;
    }

    private Map<String, Integer> sortedCounts(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private Map<String, Object> hypothesis(StrategyHypothesisIdentity value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hypothesisId", value.hypothesisId());
        result.put("hypothesisVersion", value.hypothesisVersion());
        return result;
    }

    private Map<String, Object> intent(StrategyIntent value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instrument", value.instrument());
        result.put("side", value.side().name());
        result.put("orderType", value.orderType().name());
        result.put(QUANTITY, value.quantity());
        result.put("decisionAt", value.decisionAt().toString());
        return result;
    }

    private Map<String, Object> sizing(FixedBaseQuantityPolicy value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(POLICY_VERSION, value.policyVersion());
        result.put("baseQuantity", value.baseQuantity());
        return result;
    }

    private Map<String, Object> exit(TimeHorizonExitPolicy value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(POLICY_VERSION, value.policyVersion());
        result.put("horizon", value.horizon().toString());
        return result;
    }

    private Map<String, Object> economicResult(EconomicResult value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, value.status().name());
        result.put("completeness", value.completeness().name());
        result.put("referenceNotional", value.referenceNotional());
        result.put("executionNotional", value.executionNotional());
        result.put("execution", execution(value.execution()));
        result.put("costs", costs(value));
        result.put("intent", value.intent() == null ? null : intent(value.intent()));
        result.put("entryMarketState", marketState(value.entryMarketState()));
        result.put("exitMarketState", marketState(value.exitMarketState()));
        result.put("entryExecution", execution(value.entryExecution()));
        result.put("exitExecution", execution(value.exitExecution()));
        result.put("executionStatus", value.executionStatus() == null ? null : value.executionStatus().name());
        result.put("referenceMarketReturn", value.referenceMarketReturn());
        result.put("grossStrategyPnl", value.grossStrategyPnl());
        result.put("executionAdjustedPnl", value.executionAdjustedPnl());
        result.put("netPnl", value.netPnl());
        result.put("netReturn", value.netReturn());
        result.put(EXECUTION_MODEL_VERSION, value.executionModelVersion());
        result.put(TRANSACTION_COST_MODEL_VERSION, value.transactionCostModelVersion());
        result.put("calculationFingerprint", value.calculationFingerprint());
        result.put(REASON, value.reason());
        result.put("closedQuantity", value.closedQuantity());
        result.put("residualExposure", value.residualExposure());
        return result;
    }

    @SuppressWarnings("java:S1168")
    private Map<String, Object> execution(SimulatedExecution value) {
        if (value == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("side", value.side().name());
        result.put("requestedQuantity", value.requestedQuantity());
        result.put("filledQuantity", value.filledQuantity());
        result.put("averageExecutionPrice", value.averageExecutionPrice());
        result.put("executedAt", value.executedAt().toString());
        result.put(STATUS, value.status().name());
        result.put(EXECUTION_MODEL_VERSION, value.executionModelVersion());
        result.put("sourceFingerprint", value.sourceFingerprint());
        result.put("fills", value.fills().stream().map(fill -> {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put(QUANTITY, fill.quantity());
            serialized.put("price", fill.price());
            serialized.put("executedAt", fill.executedAt().toString());
            return serialized;
        }).toList());
        result.put(REASON, value.reason());
        result.put("executionFingerprint", value.executionFingerprint());
        return result;
    }

    @SuppressWarnings("java:S1168")
    private Map<String, Object> costs(EconomicResult value) {
        if (value.costs() == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionImpact", value.costs().executionImpact());
        result.put("commission", cost(value.costs().commissionComponent()));
        result.put("funding", cost(value.costs().fundingComponent()));
        result.put("otherExplicitCosts", cost(value.costs().otherExplicitCosts()));
        result.put("explicitTotal", value.costs().explicitTotal());
        result.put(TRANSACTION_COST_MODEL_VERSION, value.costs().transactionCostModelVersion());
        return result;
    }

    @SuppressWarnings("java:S1168")
    private Map<String, Object> cost(ExplicitCost value) {
        if (value == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, value.status().name());
        result.put("amount", value.amount());
        result.put("evidence", value.evidence() == null ? null : evidence(value.evidence()));
        result.put(POLICY_VERSION, value.policyVersion());
        return result;
    }

    private Map<String, Object> evidence(CostEvidence value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fingerprint", value.fingerprint());
        result.put("sourceId", value.sourceId());
        result.put("effectiveFrom", value.effectiveFrom().toString());
        result.put("effectiveTo", value.effectiveTo() == null ? null : value.effectiveTo().toString());
        result.put("knownAt", value.knownAt().toString());
        return result;
    }

    @SuppressWarnings("java:S1168")
    private Map<String, Object> marketState(ReferenceMarketState value) {
        if (value == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("marketId", value.marketId());
        result.put("instrument", value.instrument());
        result.put("evaluationCutoff", value.evaluationCutoff().toString());
        result.put("bid", value.bid());
        result.put("ask", value.ask());
        result.put("referencePrice", value.referencePrice());
        result.put("productType", value.productType().name());
        result.put("knownAt", value.knownAt().toString());
        result.put("evidenceFingerprint", value.evidenceFingerprint());
        result.put("bids", value.bids().stream().map(this::level).toList());
        result.put("asks", value.asks().stream().map(this::level).toList());
        result.put("sequenceId", value.sequenceId());
        result.put("sourceStartOrdinal", value.sourceStartOrdinal());
        result.put("sourceEndOrdinal", value.sourceEndOrdinal());
        result.put("provider", value.provider());
        result.put("providerSymbol", value.providerSymbol());
        result.put("normalizationVersion", value.normalizationVersion());
        return result;
    }

    private Map<String, Object> level(ReferenceMarketState.Level value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("price", value.price());
        result.put(QUANTITY, value.quantity());
        return result;
    }

    private String write(Map<String, Object> document, String subject) {
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize " + subject, exception);
        }
    }
}
