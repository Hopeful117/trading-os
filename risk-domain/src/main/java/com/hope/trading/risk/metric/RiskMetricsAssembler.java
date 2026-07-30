package com.hope.trading.risk.metric;

public final class RiskMetricsAssembler {
    public RiskMetrics assemble(ObservedMetrics observed, ProjectedMetrics projected,
                                DerivedMetrics derived) {
        return new RiskMetrics(
                observed.balance(), observed.equity(), observed.floatingPnl(),
                observed.closedPnl(), observed.margin(), observed.freeMargin(),
                projected.exposure(), projected.drawdown(), projected.margin(),
                derived.remainingRisk(), derived.portfolioHeat(), derived.riskUtilization(),
                derived.positionRiskRatio(), derived.exposureRatio(),
                derived.dailyDrawdownRatio());
    }
}
