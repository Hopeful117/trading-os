package com.hope.trading.risk.engine;

import com.hope.trading.risk.rule.*;
import java.time.Clock;
import java.util.List;

public final class RiskEngines {
    private RiskEngines() {}
    public static RiskEngine standard(String version, Clock clock) {
        return new DeterministicRiskEngine(version, new RiskRuleRegistry(List.of(
                new MaximumPositionRiskRule(), new MaximumExposureRule(),
                new DailyDrawdownRule())), clock);
    }
}
