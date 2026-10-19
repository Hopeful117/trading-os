package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.tradingcore.EconomicResearchClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Market Intelligence owns bounded population orchestration and snapshot resolution. */
@Service
public final class EconomicResearchOrchestrationService {
    private static final int MAX_CANDIDATES = 1_000;
    private static final int MAX_BUNDLE_BYTES = 4 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final ExactFeatureSnapshotResolver snapshots;
    private final EconomicResearchClient tradingCore;

    public EconomicResearchOrchestrationService(ObjectMapper mapper, ExactFeatureSnapshotResolver snapshots,
                                                EconomicResearchClient tradingCore) {
        this.mapper = Objects.requireNonNull(mapper, "object mapper");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.tradingCore = Objects.requireNonNull(tradingCore, "trading core client");
    }

    public EconomicResearchClient.EvaluationResponse evaluate(String candidateJsonl, Instant researchCutoff,
                                                              String costModelVersion) {
        return evaluate(candidateJsonl, researchCutoff, costModelVersion, null, null, null);
    }

    public EconomicResearchClient.EvaluationResponse evaluate(String candidateJsonl, Instant researchCutoff,
                                                              String costModelVersion, UUID brokerAccountId,
                                                              String provider, String productType) {
        Objects.requireNonNull(candidateJsonl, "candidateJsonl");
        boolean anyFeeContext = brokerAccountId != null || provider != null || productType != null;
        boolean completeFeeContext = brokerAccountId != null && provider != null && !provider.isBlank()
                && productType != null && !productType.isBlank();
        if (anyFeeContext && !completeFeeContext) {
            throw new IllegalArgumentException("brokerAccountId, provider and productType must be supplied together");
        }
        if (candidateJsonl.getBytes(StandardCharsets.UTF_8).length > MAX_BUNDLE_BYTES) {
            throw new IllegalArgumentException("candidate bundle exceeds the configured size bound");
        }
        List<String> lines = new ArrayList<>();
        String[] inputLines = candidateJsonl.split("\\R", -1);
        if (inputLines.length > MAX_CANDIDATES) {
            throw new IllegalArgumentException("candidate population exceeds the configured bound");
        }
        for (String line : inputLines) {
            if (line.isBlank()) throw new IllegalArgumentException("blank candidate lines are not allowed");
            try {
                JsonNode root = mapper.readTree(line);
                JsonNode feature = requiredObject(root, "featureSnapshot");
                snapshots.resolve(UUID.fromString(requiredText(feature, "marketId")),
                        Instant.parse(requiredText(feature, "evaluationCutoff")), requiredText(feature, "featureProjectionVersion"),
                        requiredText(feature, "contextFingerprint"), requiredText(feature, "featureSnapshotFingerprint"),
                        requiredText(feature, "identityFingerprint"));
                lines.add(line);
            } catch (Exception exception) {
                throw new IllegalArgumentException("candidate rejected before Trading Core evaluation", exception);
            }
        }
        String normalized = String.join("\n", lines);
        EconomicResearchClient.EvaluationRequest request = brokerAccountId == null && provider == null
                && productType == null
                ? new EconomicResearchClient.EvaluationRequest(normalized, researchCutoff.toString(), costModelVersion)
                : new EconomicResearchClient.EvaluationRequest(normalized, researchCutoff.toString(), costModelVersion,
                brokerAccountId, provider, productType);
        return tradingCore.evaluate(request);
    }

    private JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isObject()) throw new IllegalArgumentException(field + " must be an object");
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.textValue();
    }
}
