package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.model.RiskRuleDashboardView;
import com.hope.trading.trading_core.dashboard.model.RiskStatus;

import java.util.List;

public record DashboardRiskEvaluation(
        RiskStatus status,
        List<RiskRuleDashboardView> rules
) {
    public DashboardRiskEvaluation {
        rules = List.copyOf(rules);
    }
}
