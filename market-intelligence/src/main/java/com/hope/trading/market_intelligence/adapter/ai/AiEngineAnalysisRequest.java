package com.hope.trading.market_intelligence.adapter.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record AiEngineAnalysisRequest(
        UUID executionId,
        List<String> capabilities,
        IntelligenceContext authorizedContext,
        MarketThesisWire thesis,
        PolicyWire policy,
        TraceWire trace,
        String idempotencyKey
) {
    static AiEngineAnalysisRequest from(
            com.hope.trading.market_intelligence.application.port.AiAnalysisCommand command) {
        return from(command, 256_000, 1_500);
    }

    static AiEngineAnalysisRequest from(
            com.hope.trading.market_intelligence.application.port.AiAnalysisCommand command,
            int contextLimitBytes,
            int maxOutputTokens) {
        return new AiEngineAnalysisRequest(
                command.executionId(), command.capabilities(), command.authorizedContext(),
                MarketThesisWire.from(command.thesis()),
                PolicyWire.from(command.policy(), contextLimitBytes, maxOutputTokens),
                TraceWire.from(command.trace()), command.executionId().toString());
    }

    record MarketThesisWire(
            UUID thesisId, UUID lineageId, long version, UUID marketId, String instrument,
            String state, ScenarioWire scenario, List<EvidenceWire> evidence, List<String> reasons,
            Instant evaluatedAt, Instant sourceCutoff, Instant validFrom, Instant validUntil,
            String composerVersion, String inputFingerprint
    ) {
        static MarketThesisWire from(MarketThesis thesis) {
            return new MarketThesisWire(
                    thesis.id(), thesis.lineageId(), thesis.version(), thesis.marketId(),
                    thesis.instrument(), thesis.state().name(),
                    thesis.scenario().map(ScenarioWire::from).orElse(null),
                    thesis.evidence().stream().map(EvidenceWire::from).toList(), thesis.reasons(),
                    thesis.evaluatedAt(), thesis.sourceCutoff(), thesis.validFrom(),
                    thesis.validUntil().orElse(null), thesis.composerVersion(), thesis.inputFingerprint());
        }
    }

    record ScenarioWire(
            String direction, String narrative, List<String> confirmationConditions,
            List<String> invalidationConditions
    ) {
        static ScenarioWire from(com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario value) {
            return new ScenarioWire(value.direction().name(), value.statement(),
                    value.confirmationConditions().stream()
                            .map(com.hope.trading.market_intelligence.domain.thesis.MarketThesisCondition::explanation)
                            .toList(),
                    value.invalidationConditions().stream()
                            .map(com.hope.trading.market_intelligence.domain.thesis.MarketThesisCondition::explanation)
                            .toList());
        }
    }

    record EvidenceWire(
            UUID evidenceId, String role, String availability, String severity, String source,
            String reference, String timeframe, String explanation, Instant observedAt,
            Instant validUntil, String provider, String instrument, String sourceFingerprint,
            String ruleVersion, Instant sourceCutoff, boolean required
    ) {
        static EvidenceWire from(com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence value) {
            return new EvidenceWire(value.evidenceId(), value.role().name(), value.availability().name(),
                    value.severity() == null ? null : value.severity().name(), value.source(),
                    value.reference(), value.timeframe(), value.explanation(), value.observedAt(),
                    value.validUntil(), value.provider(), value.instrument(), value.sourceFingerprint(),
                    value.ruleVersion(), value.sourceCutoff(), value.required());
        }
    }

    record PolicyWire(
            @JsonProperty("maximumDurationMs") long maximumDurationMs,
            @JsonProperty("maximumAiRequests") int maximumAiRequests,
            @JsonProperty("contextLimitBytes") int contextLimitBytes,
            @JsonProperty("maxOutputTokens") int maxOutputTokens
    ) {
        static PolicyWire from(com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionPolicy value,
                               int contextLimitBytes, int maxOutputTokens) {
            return new PolicyWire(value.maximumDuration().toMillis(), value.maximumAiRequests(),
                    contextLimitBytes, maxOutputTokens);
        }
    }

    record TraceWire(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("contractVersion") String contractVersion
    ) {
        static TraceWire from(com.hope.trading.market_intelligence.domain.security.ExecutionTrace value) {
            return new TraceWire(value.requestId(), value.traceId(), value.contractVersion());
        }
    }
}
