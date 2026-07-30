package com.hope.trading.risk.audit;

import com.hope.trading.risk.engine.DeterministicRiskEngine;
import com.hope.trading.risk.rule.RiskRuleRegistry;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Objects;

public final class RiskReplayService {
    private final String engineVersion;
    private final RiskRuleRegistry registry;
    public RiskReplayService(String engineVersion, RiskRuleRegistry registry) {
        this.engineVersion = Objects.requireNonNull(engineVersion);
        this.registry = Objects.requireNonNull(registry);
    }
    public boolean reproduces(RiskEvaluationRecord record) {
        var clock = Clock.fixed(record.result().evaluatedAt(), ZoneOffset.UTC);
        var replayed = new DeterministicRiskEngine(engineVersion, registry, clock)
                .evaluate(record.context());
        return replayed.equals(record.result());
    }
}
