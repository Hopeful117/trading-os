package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.*;

public interface CapabilityExecutor {
    CapabilityExecutionHandle submit(Capability capability, CapabilityContext context);
}
