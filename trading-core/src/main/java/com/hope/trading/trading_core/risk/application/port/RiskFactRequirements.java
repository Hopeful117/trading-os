package com.hope.trading.trading_core.risk.application.port;

import com.hope.trading.risk.domain.RiskRuleIds;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import java.util.EnumSet;
import java.util.Set;

/** Facts required by the selected deterministic risk policy. */
public record RiskFactRequirements(
        boolean balance,
        boolean equity,
        boolean positions,
        boolean startingBalance,
        boolean usedMargin,
        boolean freeMargin,
        boolean closedDeals,
        Set<EquityProvenance> admissibleEquityProvenances,
        boolean equityContractRequired
) {
    public RiskFactRequirements {
        admissibleEquityProvenances = Set.copyOf(admissibleEquityProvenances == null
                ? Set.of(EquityProvenance.BROKER_REPORTED) : admissibleEquityProvenances);
    }

    public RiskFactRequirements(boolean balance, boolean equity, boolean positions,
                                boolean startingBalance, boolean usedMargin, boolean freeMargin,
                                boolean closedDeals) {
        this(balance, equity, positions, startingBalance, usedMargin, freeMargin, closedDeals,
                EnumSet.of(EquityProvenance.BROKER_REPORTED), false);
    }

    public enum EquityProvenance { BROKER_REPORTED, BROKER_DERIVED, UNAVAILABLE }

    public static RiskFactRequirements from(EffectiveRiskRuleSet rules) {
        boolean balance = false;
        boolean equity = false;
        boolean positions = false;
        boolean startingBalance = false;
        boolean usedMargin = false;
        boolean freeMargin = false;
        boolean closedDeals = false;
        for (var rule : rules.rules()) {
            switch (rule.ruleId()) {
                case RiskRuleIds.MAX_POSITION_RISK -> {
                    balance = true;
                    positions = true;
                }
                case RiskRuleIds.MAX_EXPOSURE -> {
                    equity = true;
                    positions = true;
                }
                case RiskRuleIds.DAILY_DRAWDOWN -> {
                    balance = true;
                    equity = true;
                }
                case RiskRuleIds.MAX_TOTAL_DRAWDOWN -> {
                    equity = true;
                    startingBalance = true;
                }
                default -> throw new IllegalArgumentException("Unsupported risk rule: " + rule.ruleId());
            }
        }
        return new RiskFactRequirements(balance, equity, positions, startingBalance,
                usedMargin, freeMargin, closedDeals, Set.of(EquityProvenance.BROKER_REPORTED), true);
    }
}
