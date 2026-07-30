package com.hope.trading.risk.explain;

import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import com.hope.trading.risk.rule.RuleExplanation;
import java.util.List;

public record DecisionExplanation(RiskDecision decision, List<RuleExplanation> reasons) {
    public DecisionExplanation { reasons = List.copyOf(reasons); }
}
