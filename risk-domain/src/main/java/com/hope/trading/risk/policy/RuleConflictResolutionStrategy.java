package com.hope.trading.risk.policy;

@FunctionalInterface
public interface RuleConflictResolutionStrategy {
    RuleConfiguration resolve(RuleConfiguration higherAuthority,
                              RuleConfiguration lowerAuthority);
}
