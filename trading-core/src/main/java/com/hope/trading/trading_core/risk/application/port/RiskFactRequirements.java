package com.hope.trading.trading_core.risk.application.port;

import com.hope.trading.risk.domain.RiskRuleIds;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import java.util.EnumSet;
import java.util.Map;
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
        boolean equityContractRequired,
        Map<String, InvariantRequirements> invariants
) {
    public RiskFactRequirements {
        admissibleEquityProvenances = Set.copyOf(admissibleEquityProvenances == null
                ? Set.of(EquityProvenance.BROKER_REPORTED) : admissibleEquityProvenances);
        invariants = Map.copyOf(invariants == null ? Map.of() : invariants);
    }

    public RiskFactRequirements(boolean balance, boolean equity, boolean positions,
                                boolean startingBalance, boolean usedMargin, boolean freeMargin,
                                boolean closedDeals) {
        this(balance, equity, positions, startingBalance, usedMargin, freeMargin, closedDeals,
                EnumSet.of(EquityProvenance.BROKER_REPORTED), false, Map.of());
    }

    public RiskFactRequirements(boolean balance, boolean equity, boolean positions,
                                boolean startingBalance, boolean usedMargin, boolean freeMargin,
                                boolean closedDeals, Set<EquityProvenance> admissibleEquityProvenances,
                                boolean equityContractRequired) {
        this(balance, equity, positions, startingBalance, usedMargin, freeMargin, closedDeals,
                admissibleEquityProvenances, equityContractRequired, Map.of());
    }

    public enum EquityProvenance { BROKER_REPORTED, BROKER_DERIVED, UNAVAILABLE }

    public enum Fact { BALANCE, EQUITY, POSITIONS, STARTING_BALANCE, CLOSED_DEALS, PROTECTIVE_STOPS }

    public record InvariantRequirements(Set<Fact> facts, boolean mandatory) {
        public InvariantRequirements {
            facts = Set.copyOf(facts == null ? Set.of() : facts);
        }
    }

    public static RiskFactRequirements from(EffectiveRiskRuleSet rules) {
        boolean balance = false;
        boolean equity = false;
        boolean positions = false;
        boolean startingBalance = false;
        boolean usedMargin = false;
        boolean freeMargin = false;
        boolean closedDeals = false;
        Map<String, InvariantRequirements> invariants = new java.util.LinkedHashMap<>();
        for (var rule : rules.rules()) {
            switch (rule.ruleId()) {
                case RiskRuleIds.MAX_POSITION_RISK -> {
                    balance = true;
                    positions = true;
                    invariants.put(rule.ruleId(), new InvariantRequirements(
                            Set.of(Fact.BALANCE, Fact.POSITIONS, Fact.PROTECTIVE_STOPS), true));
                }
                case RiskRuleIds.MAX_EXPOSURE -> {
                    equity = true;
                    positions = true;
                    invariants.put(rule.ruleId(), new InvariantRequirements(
                            Set.of(Fact.EQUITY, Fact.POSITIONS), true));
                }
                case RiskRuleIds.DAILY_DRAWDOWN -> {
                    balance = true;
                    equity = true;
                    invariants.put(rule.ruleId(), new InvariantRequirements(
                            Set.of(Fact.BALANCE, Fact.EQUITY), true));
                }
                case RiskRuleIds.MAX_TOTAL_DRAWDOWN -> {
                    equity = true;
                    startingBalance = true;
                    invariants.put(rule.ruleId(), new InvariantRequirements(
                            Set.of(Fact.EQUITY, Fact.STARTING_BALANCE), true));
                }
                default -> throw new IllegalArgumentException("Unsupported risk rule: " + rule.ruleId());
            }
        }
        return new RiskFactRequirements(balance, equity, positions, startingBalance,
                usedMargin, freeMargin, closedDeals, Set.of(EquityProvenance.BROKER_REPORTED), true,
                invariants);
    }
}
