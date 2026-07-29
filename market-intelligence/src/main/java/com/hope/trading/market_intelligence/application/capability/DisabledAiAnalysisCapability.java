package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.application.port.AiEnginePort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DisabledAiAnalysisCapability implements AiAnalysisCapability {
    public static final String CAPABILITY_ID = "ai-market-reasoning";
    private final AiEnginePort aiEnginePort;

    public DisabledAiAnalysisCapability(AiEnginePort aiEnginePort) {
        this.aiEnginePort = aiEnginePort;
    }

    @Override
    public String id() {
        return CAPABILITY_ID;
    }

    @Override
    public Set<AnalysisExecutionMode> supportedModes() {
        return Set.of(AnalysisExecutionMode.PASSIVE, AnalysisExecutionMode.ACTIVE);
    }

    @Override
    public List<ContextRequirement> requirements(AnalysisExecutionMode mode) {
        List<ContextRequirement> requirements = new ArrayList<>();
        requirements.add(ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY));
        requirements.add(ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT));
        if (mode == AnalysisExecutionMode.ACTIVE) {
            requirements.add(ContextRequirement.optionalPublic(ContextSectionType.HISTORICAL_OHLC));
        }
        return List.copyOf(requirements);
    }

    @Override
    public CapabilityAvailability availability() {
        return aiEnginePort.available()
                ? CapabilityAvailability.AVAILABLE
                : CapabilityAvailability.UNAVAILABLE;
    }

    @Override
    public CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request,
            IntelligenceContext context
    ) {
        return aiEnginePort.analyze(request, context);
    }
}
