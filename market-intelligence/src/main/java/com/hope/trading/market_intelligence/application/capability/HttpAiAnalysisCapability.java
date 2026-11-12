package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.application.port.AiAnalysisCommand;
import com.hope.trading.market_intelligence.application.port.AiEnginePort;
import com.hope.trading.market_intelligence.application.port.AiExecutionProgress;
import com.hope.trading.market_intelligence.application.port.AiExecutionReference;
import com.hope.trading.market_intelligence.application.port.AiInterpretationPhase;
import com.hope.trading.market_intelligence.application.port.AiSynthesisContext;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.security.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "ai.engine.enabled", havingValue = "true")
public class HttpAiAnalysisCapability implements AiAnalysisCapability {
    public static final String CAPABILITY_ID = DisabledAiAnalysisCapability.CAPABILITY_ID;
    private final AiEnginePort aiEnginePort;

    public HttpAiAnalysisCapability(AiEnginePort aiEnginePort) {
        this.aiEnginePort = aiEnginePort;
    }

    @Override
    public String id() { return CAPABILITY_ID; }

    @Override
    public Set<AnalysisExecutionMode> supportedModes() {
        return Set.of(AnalysisExecutionMode.PASSIVE, AnalysisExecutionMode.ACTIVE);
    }

    @Override
    public List<ContextRequirement> requirements(AnalysisExecutionMode mode) {
        return List.of(
                ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY),
                ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
                ContextRequirement.optionalPublic(ContextSectionType.HISTORICAL_OHLC));
    }

    @Override
    public CapabilityAvailability availability() {
        return aiEnginePort.available()
                ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE;
    }

    @Override
    public CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request, IntelligenceContext context) {
        return execute(request, context, false, AiInterpretationPhase.INITIAL, null);
    }

    @Override
    public CapabilityAnalysisResult analyzeAdaptive(
            IntelligenceAnalysisRequest request, IntelligenceContext context) {
        return execute(request, context, true, AiInterpretationPhase.INITIAL, null);
    }

    public CapabilityAnalysisResult analyzeFinal(
            IntelligenceAnalysisRequest request, IntelligenceContext context,
            AiSynthesisContext synthesisContext) {
        return execute(request, context, false, AiInterpretationPhase.FINAL_SYNTHESIS, synthesisContext);
    }

    private CapabilityAnalysisResult execute(
            IntelligenceAnalysisRequest request, IntelligenceContext context, boolean adaptive,
            AiInterpretationPhase phase, AiSynthesisContext synthesisContext) {
        if (request.thesis() == null) {
            return CapabilityAnalysisResult.empty("Market Thesis is required for AI interpretation");
        }
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        AiExecutionReference reference = aiEnginePort.submit(new AiAnalysisCommand(
                request.analysisId(), List.of(CAPABILITY_ID), context, request.thesis(), adaptive, phase,
                synthesisContext,
                new AnalysisExecutionPolicy(
                        Duration.ofSeconds(30), Duration.ofSeconds(30), 1, 1,
                        new ContextLimits(32, 10_000, 1_000, 8, ContextClassification.PUBLIC),
                        new RetryPolicy(0, Duration.ZERO, Set.of()),
                        java.util.Map.of(),
                        new DegradationPolicy(true, true, true, true)),
                new ExecutionTrace(request.analysisId(), request.analysisId().toString(),
                        request.analysisId().toString(), ServiceIdentity.marketIntelligence(),
                        AuthorizedCapability.SUBMIT_ANALYSIS, "v1", Instant.now())));
        while (Instant.now().isBefore(deadline)) {
            var result = aiEnginePort.result(reference);
            if (result.isPresent()) {
                var completed = result.orElseThrow();
                CapabilityAnalysisResult interpreted = completed.result();
                return new CapabilityAnalysisResult(interpreted.findings(), interpreted.warnings(),
                        interpreted.requestedAnalysis(), interpreted.reconciliations(), completed.provenance());
            }
            AiExecutionProgress progress = aiEnginePort.progress(reference);
            if ("FAILED".equals(progress.status()) || "CANCELLED".equals(progress.status())) {
                return CapabilityAnalysisResult.empty("AI Engine execution " + progress.status());
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                aiEnginePort.cancel(reference);
                return CapabilityAnalysisResult.empty("AI Engine execution interrupted");
            }
        }
        aiEnginePort.cancel(reference);
        return CapabilityAnalysisResult.empty("AI Engine execution timed out");
    }
}
