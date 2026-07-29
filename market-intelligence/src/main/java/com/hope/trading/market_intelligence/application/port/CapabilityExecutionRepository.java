package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;

import java.util.*;

public interface CapabilityExecutionRepository {
    CapabilityExecution save(CapabilityExecution execution);
    List<CapabilityExecution> findByAnalysisExecutionId(UUID analysisExecutionId);
}
