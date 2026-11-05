package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.IntelligenceContext;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.security.*;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.capability.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "ai.engine.enabled", havingValue = "true")
public final class HttpAiEngineDomainCapability implements Capability {
    private final AiEnginePort aiEnginePort;

    public HttpAiEngineDomainCapability(AiEnginePort aiEnginePort) {
        this.aiEnginePort = aiEnginePort;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(DisabledAiAnalysisCapability.CAPABILITY_ID),
                new CapabilityVersion("1.0.0"), CapabilityCategory.AI, ExecutionPolicy.ON_DEMAND,
                com.hope.trading.market_intelligence.domain.capability.RetryPolicy.disabled(), List.of(),
                List.of(new ProducedContribution.RecommendationContribution("ai-interpretation")),
                Duration.ofSeconds(35), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        Object thesisValue = context.parameters().get("marketThesis");
        Object authorizedContextValue = context.parameters().get("authorizedContext");
        if (!(thesisValue instanceof MarketThesis thesis)
                || !(authorizedContextValue instanceof IntelligenceContext authorizedContext)) {
            return CapabilityResult.noOpportunity(
                    List.of("Market Thesis and authorized context are required for AI interpretation"),
                    CapabilityCompleteness.DEGRADED);
        }
        Duration executionTimeout = context.parameters().get("capabilityTimeout") instanceof Duration value
                ? value : metadata().timeout();
        AiExecutionReference reference = aiEnginePort.submit(new AiAnalysisCommand(
                context.analysisExecutionId(), List.of(metadata().id().value()), authorizedContext, thesis,
                defaultPolicy(executionTimeout), new ExecutionTrace(
                        context.analysisExecutionId(), context.analysisExecutionId().toString(),
                        context.analysisExecutionId().toString(), ServiceIdentity.marketIntelligence(),
                        AuthorizedCapability.SUBMIT_ANALYSIS, "v1", Instant.now())));
        Instant deadline = Instant.now().plus(executionTimeout);
        while (Instant.now().isBefore(deadline)) {
            var result = aiEnginePort.result(reference);
            if (result.isPresent()) {
                return new CapabilityResult(List.of(), List.of(), Map.of(), List.of(),
                        CapabilityCompleteness.COMPLETE, result.orElseThrow().result().findings());
            }
            AiExecutionProgress progress = aiEnginePort.progress(reference);
            if ("FAILED".equals(progress.status()) || "CANCELLED".equals(progress.status())) {
                return CapabilityResult.noOpportunity(
                        List.of("AI Engine execution " + progress.status() + ": " + progress.errorType()),
                        CapabilityCompleteness.DEGRADED);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                aiEnginePort.cancel(reference);
                return CapabilityResult.noOpportunity(
                        List.of("AI Engine execution interrupted"), CapabilityCompleteness.DEGRADED);
            }
        }
        aiEnginePort.cancel(reference);
        return CapabilityResult.noOpportunity(
                List.of("AI Engine execution timed out"), CapabilityCompleteness.DEGRADED);
    }

    private AnalysisExecutionPolicy defaultPolicy(Duration timeout) {
        return new AnalysisExecutionPolicy(
                timeout, timeout, 1, 1,
                new ContextLimits(32, 10_000, 1_000, 8, ContextClassification.PUBLIC),
                new com.hope.trading.market_intelligence.domain.execution.RetryPolicy(
                        0, Duration.ZERO, Set.of()), Map.of(),
                new DegradationPolicy(true, true, true, true));
    }
}
