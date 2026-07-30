package com.hope.trading.risk.snapshot;

import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import java.time.Instant;
import java.util.Objects;

public record RuleSetSnapshot(long version, Instant capturedAt,
                              EffectiveRiskRuleSet effectiveRuleSet) {
    public RuleSetSnapshot {
        if (version < 1) throw new IllegalArgumentException("version starts at 1");
        Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(effectiveRuleSet);
    }
}
