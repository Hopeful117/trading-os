package com.hope.trading.market_intelligence.application.artifact;

import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;

public interface ArtifactResolver {
    ArtifactResolution resolve(
            ArtifactRequirement requirement,
            AnalysisExecution execution
    );
}
