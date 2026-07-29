package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;

import java.util.List;
import java.util.Set;

public interface AnalysisCapability {
    String id();

    AnalysisOrigin origin();

    Set<AnalysisExecutionMode> supportedModes();

    List<ContextRequirement> requirements(AnalysisExecutionMode mode);

    CapabilityAvailability availability();

    CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request,
            IntelligenceContext context
    );
}
