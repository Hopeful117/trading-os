package com.hope.trading.trading_core.execution.application.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.execution.domain.economic.CostCompleteness;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.research.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Strict, bounded importer for the offline economic candidate contract. */
public final class EconomicResearchCandidateJsonlImporter {
    public static final String SCHEMA_VERSION = "economic-research-candidate-v1";
    public static final int DEFAULT_MAX_CANDIDATES = 1_000;
    private static final Set<String> ROOT = Set.of("schemaVersion", "datasetDefinition", "datasetDefinitionFingerprint",
            "featureSnapshot", "hypothesis", "intent", "sizingPolicy", "exitPolicy", "executionModelVersion",
            "transactionCostModelVersion", "candidateFingerprint");
    private final ObjectMapper mapper;
    private final int maxCandidates;

    public EconomicResearchCandidateJsonlImporter(ObjectMapper mapper) {
        this(mapper, DEFAULT_MAX_CANDIDATES);
    }

    public EconomicResearchCandidateJsonlImporter(ObjectMapper mapper, int maxCandidates) {
        this.mapper = Objects.requireNonNull(mapper, "object mapper");
        if (maxCandidates <= 0) throw new IllegalArgumentException("maxCandidates must be positive");
        this.maxCandidates = maxCandidates;
    }

    public List<EconomicResearchCandidate> importLines(String jsonl) {
        Objects.requireNonNull(jsonl, "jsonl");
        List<EconomicResearchCandidate> candidates = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        String[] lines = jsonl.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank()) throw invalid(index, "blank lines are not allowed");
            if (candidates.size() == maxCandidates) throw invalid(index, "candidate population exceeds the configured bound");
            try {
                JsonNode root = mapper.readTree(lines[index]);
                requireObject(root, "candidate");
                rejectUnknown(root, ROOT, "candidate");
                require(root, "schemaVersion").textValue().equals(SCHEMA_VERSION);
                EconomicResearchCandidate candidate = candidate(root);
                String definitionFingerprint = text(root, "datasetDefinitionFingerprint");
                if (!definitionFingerprint.equals(candidate.datasetDefinition().fingerprint())) {
                    throw new IllegalArgumentException("datasetDefinitionFingerprint does not match definition semantics");
                }
                String supplied = text(root, "candidateFingerprint");
                if (supplied != null && !supplied.equals(candidate.fingerprint())) {
                    throw new IllegalArgumentException("candidateFingerprint does not match candidate semantics");
                }
                if (!fingerprints.add(candidate.fingerprint())) throw new IllegalArgumentException("duplicate candidate fingerprint");
                candidates.add(candidate);
            } catch (Exception exception) {
                if (exception instanceof IllegalArgumentException illegal) throw invalid(index, illegal.getMessage(), illegal);
                throw invalid(index, "malformed JSON", exception);
            }
        }
        return List.copyOf(candidates);
    }

    private EconomicResearchCandidate candidate(JsonNode root) {
        JsonNode definition = object(root, "datasetDefinition");
        EconomicResearchDatasetDefinition dataset = definition(definition);
        JsonNode feature = object(root, "featureSnapshot");
        rejectUnknown(feature, Set.of("marketId", "evaluationCutoff", "featureProjectionVersion", "contextFingerprint",
                "featureSnapshotFingerprint", "identityFingerprint"), "featureSnapshot");
        FeatureSnapshotReference snapshot = new FeatureSnapshotReference(uuid(feature, "marketId"), instant(feature, "evaluationCutoff"),
                text(feature, "featureProjectionVersion"), text(feature, "contextFingerprint"),
                text(feature, "featureSnapshotFingerprint"), text(feature, "identityFingerprint"));
        JsonNode hypothesisNode = object(root, "hypothesis");
        rejectUnknown(hypothesisNode, Set.of("hypothesisId", "hypothesisVersion"), "hypothesis");
        StrategyHypothesisIdentity hypothesis = new StrategyHypothesisIdentity(text(hypothesisNode, "hypothesisId"), text(hypothesisNode, "hypothesisVersion"));
        JsonNode intentNode = object(root, "intent");
        rejectUnknown(intentNode, Set.of("instrument", "side", "orderType", "quantity", "decisionAt"), "intent");
        StrategyIntent intent = new StrategyIntent(text(intentNode, "instrument"), enumValue(intentNode, "side", StrategyIntent.Side.class),
                enumValue(intentNode, "orderType", StrategyIntent.OrderType.class), decimal(intentNode, "quantity"), instant(intentNode, "decisionAt"));
        JsonNode sizingNode = object(root, "sizingPolicy");
        rejectUnknown(sizingNode, Set.of("policyVersion", "baseQuantity"), "sizingPolicy");
        FixedBaseQuantityPolicy sizing = new FixedBaseQuantityPolicy(text(sizingNode, "policyVersion"), decimal(sizingNode, "baseQuantity"));
        JsonNode exitNode = object(root, "exitPolicy");
        rejectUnknown(exitNode, Set.of("policyVersion", "horizon"), "exitPolicy");
        TimeHorizonExitPolicy exit = new TimeHorizonExitPolicy(text(exitNode, "policyVersion"), Duration.parse(text(exitNode, "horizon")));
        String execution = text(root, "executionModelVersion");
        String costs = text(root, "transactionCostModelVersion");
        return new EconomicResearchCandidate(dataset, snapshot, hypothesis, intent, sizing, exit, execution, costs);
    }

    private EconomicResearchDatasetDefinition definition(JsonNode node) {
        rejectUnknown(node, Set.of("definitionVersion", "populationPolicyVersion", "marketIds", "startCutoffInclusive", "endCutoffExclusive",
                "featureProjectionVersion", "evidencePolicyVersion", "hypothesis", "sizingPolicy", "entryPolicyVersion", "exitPolicy",
                "executionModelVersion", "transactionCostModelVersion", "researchCutoffPolicyVersion", "requiredCostCompleteness",
                "acceptedExecutionStatuses", "objectiveOutcomeJoinPolicyVersion"), "datasetDefinition");
        JsonNode hypothesis = object(node, "hypothesis");
        JsonNode sizing = object(node, "sizingPolicy");
        JsonNode exit = object(node, "exitPolicy");
        List<UUID> markets = new ArrayList<>();
        require(node, "marketIds").forEach(value -> markets.add(UUID.fromString(value.textValue())));
        Set<SimulatedExecutionStatus> statuses = new HashSet<>();
        require(node, "acceptedExecutionStatuses").forEach(value -> statuses.add(SimulatedExecutionStatus.valueOf(value.textValue())));
        return new EconomicResearchDatasetDefinition(text(node, "definitionVersion"), text(node, "populationPolicyVersion"), markets,
                instant(node, "startCutoffInclusive"), instant(node, "endCutoffExclusive"), text(node, "featureProjectionVersion"),
                text(node, "evidencePolicyVersion"), new StrategyHypothesisIdentity(text(hypothesis, "hypothesisId"), text(hypothesis, "hypothesisVersion")),
                new FixedBaseQuantityPolicy(text(sizing, "policyVersion"), decimal(sizing, "baseQuantity")), text(node, "entryPolicyVersion"),
                new TimeHorizonExitPolicy(text(exit, "policyVersion"), Duration.parse(text(exit, "horizon"))), text(node, "executionModelVersion"),
                text(node, "transactionCostModelVersion"), text(node, "researchCutoffPolicyVersion"),
                CostCompleteness.valueOf(text(node, "requiredCostCompleteness")), statuses, text(node, "objectiveOutcomeJoinPolicyVersion", true));
    }

    private JsonNode require(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private JsonNode object(JsonNode node, String field) { JsonNode value = require(node, field); requireObject(value, field); return value; }
    private void requireObject(JsonNode node, String subject) { if (node == null || !node.isObject()) throw new IllegalArgumentException(subject + " must be an object"); }
    private void rejectUnknown(JsonNode node, Set<String> fields, String subject) { node.fieldNames().forEachRemaining(name -> { if (!fields.contains(name)) throw new IllegalArgumentException("unknown " + subject + " field: " + name); }); }
    private String text(JsonNode node, String field) { JsonNode value = require(node, field); if (!value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(field + " must be a non-blank string"); return value.textValue(); }
    private String text(JsonNode node, String field, boolean optional) { JsonNode value = node.get(field); return value == null || value.isNull() ? null : text(node, field); }
    private Instant instant(JsonNode node, String field) { return Instant.parse(text(node, field)); }
    private UUID uuid(JsonNode node, String field) { return UUID.fromString(text(node, field)); }
    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = require(node, field);
        if (!value.isNumber() && !value.isTextual()) throw new IllegalArgumentException(field + " must be numeric");
        try { return new BigDecimal(value.asText()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " must be numeric", exception); }
    }
    private <T extends Enum<T>> T enumValue(JsonNode node, String field, Class<T> type) { return Enum.valueOf(type, text(node, field)); }
    private IllegalArgumentException invalid(int line, String reason) { return invalid(line, reason, null); }
    private IllegalArgumentException invalid(int line, String reason, Throwable cause) { return new IllegalArgumentException("invalid candidate line " + (line + 1) + ": " + reason, cause); }
}
