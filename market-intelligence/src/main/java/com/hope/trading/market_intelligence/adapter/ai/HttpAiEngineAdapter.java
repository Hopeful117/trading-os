package com.hope.trading.market_intelligence.adapter.ai;

import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.AnalysisOrigin;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.FindingType;
import com.hope.trading.market_intelligence.domain.IntelligenceFinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@ConditionalOnProperty(name = "ai.engine.enabled", havingValue = "true")
public class HttpAiEngineAdapter implements AiEnginePort {
    private final AiEngineClient client;
    private final int contextLimitBytes;
    private final int maxOutputTokens;

    public HttpAiEngineAdapter(
            AiEngineClient client,
            @Value("${ai.engine.context-limit-bytes:256000}") int contextLimitBytes,
            @Value("${ai.engine.max-output-tokens:1500}") int maxOutputTokens) {
        this.client = client;
        this.contextLimitBytes = contextLimitBytes;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AiExecutionReference submit(AiAnalysisCommand command) {
        AiEngineAcceptedResponse response = client.submit(
                AiEngineAnalysisRequest.from(command, contextLimitBytes, maxOutputTokens));
        return new AiExecutionReference(response.executionId().toString());
    }

    @Override
    public AiExecutionProgress progress(AiExecutionReference reference) {
        AiEngineProgressResponse response = client.progress(UUID.fromString(reference.value()));
        return new AiExecutionProgress(new AiExecutionReference(response.executionId().toString()),
                response.status(), 0, 1, response.updatedAt(), response.errorType());
    }

    @Override
    public Optional<AiStructuredResult> result(AiExecutionReference reference) {
        AiEngineResultResponse response = client.result(UUID.fromString(reference.value()));
        if (response.result() == null
                || !("COMPLETED".equals(response.status()) || "PARTIAL".equals(response.status()))) {
            return Optional.empty();
        }
        List<IntelligenceFinding> findings = new ArrayList<>();
        addFinding(findings, response, "market-summary", "Market summary", response.result().marketSummary(),
                FindingType.AI_FINDING);
        addFinding(findings, response, "primary-scenario", "Primary scenario", response.result().primaryScenario(),
                FindingType.SCENARIO);
        response.result().supportingArguments().forEach(argument -> addArgument(
                findings, response, "supporting", argument, FindingType.AI_FINDING));
        response.result().contradictions().forEach(argument -> addArgument(
                findings, response, "contradiction", argument, FindingType.WARNING));
        response.result().missingInformation().forEach(value -> addFinding(
                findings, response, "missing", "Missing information", value, FindingType.WARNING));
        response.result().uncertainty().forEach(value -> addFinding(
                findings, response, "uncertainty", "Uncertainty", value, FindingType.WARNING));
        response.result().confirmationConditions().forEach(value -> addFinding(
                findings, response, "confirmation", "Confirmation condition", value, FindingType.SCENARIO));
        response.result().invalidationConditions().forEach(value -> addFinding(
                findings, response, "invalidation", "Invalidation condition", value, FindingType.WARNING));
        addFinding(findings, response, "suggested-decision", "Suggested decision",
                response.result().suggestedDecision(), FindingType.AI_FINDING);
        RequestedAnalysis requested = response.result().requestedAnalysis() == null ? null
                : new RequestedAnalysis(
                        response.result().requestedAnalysis().capabilityId(),
                        response.result().requestedAnalysis().capabilityVersion(),
                        response.result().requestedAnalysis().marketId(),
                        response.result().requestedAnalysis().reason(),
                        response.result().requestedAnalysis().expectedEvidence());
        AiProvenance provenance = response.provenance() == null ? null : new AiProvenance(
                response.provenance().executionId(), response.provenance().provider(),
                response.provenance().model(), response.provenance().classification(),
                response.provenance().shadowMode(), response.provenance().invocationAt(),
                response.provenance().completionAt(), response.provenance().providerCorrelationId(),
                response.provenance().fallback(), response.provenance().failureReason(),
                response.provenance().contractVersion());
        return Optional.of(new AiStructuredResult(new AiExecutionReference(response.executionId().toString()),
                new CapabilityAnalysisResult(List.copyOf(findings), List.of(), requested,
                        response.result().reconciliations() == null ? List.of() : response.result().reconciliations(),
                        provenance, response.result().hypothesisProposals().stream().map(value ->
                                new AiHypothesisProposal(value.hypothesisId(), value.observedBehavior(),
                                        value.proposedRelationship(), value.expectedOutcome(), value.evaluationHorizon(),
                                        value.applicabilityConditions(), value.falsificationConditions(),
                                        value.supportingEvidenceReferences(), value.limitations())).toList()),
                response.acceptedAt(), provenance));
    }

    @Override
    public void cancel(AiExecutionReference reference) {
        client.cancel(UUID.fromString(reference.value()));
    }

    private void addArgument(List<IntelligenceFinding> findings, AiEngineResultResponse response,
                             String kind, ArgumentWire argument, FindingType type) {
        addFinding(findings, response, kind, kind, argument.text(), type,
                Set.copyOf(argument.evidenceRefs()));
    }

    private void addFinding(List<IntelligenceFinding> findings, AiEngineResultResponse response,
                            String id, String title, String description, FindingType type) {
        addFinding(findings, response, id, title, description, type, Set.of());
    }

    private void addFinding(List<IntelligenceFinding> findings, AiEngineResultResponse response,
                            String id, String title, String description, FindingType type,
                            Set<UUID> evidenceReferences) {
        findings.add(new IntelligenceFinding(
                response.executionId() + ":" + id + ":" + findings.size(),
                "ai-market-reasoning", AnalysisOrigin.AI, type, title, description,
                Map.of(), null, Set.of(ContextSectionType.DETERMINISTIC_FINDINGS),
                response.completedAt() == null ? Instant.now() : response.completedAt(),
                evidenceReferences));
    }
}
