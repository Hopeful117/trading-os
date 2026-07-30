package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;

public final class DefaultTradePlanIntegrationBoundary
        implements TradePlanRiskValidationBoundary, TradePlanExecutionBoundary {
    private final TradePlanRepository repository;
    private final TradePlanApplicationService service;
    public DefaultTradePlanIntegrationBoundary(
            TradePlanRepository repository, TradePlanApplicationService service) {
        this.repository = repository; this.service = service;
    }
    @Override public TradePlan loadAcceptedSnapshot(TradePlanId id, TradePlanVersion version) {
        return exact(id, version, TradePlanStatus.ACCEPTED);
    }
    @Override public TradePlan recordRiskValidated(
            TradePlanId id, TradePlanVersion acceptedVersion) {
        exactLatest(id, acceptedVersion, TradePlanStatus.ACCEPTED);
        return service.transition(id, TradePlanStatus.RISK_VALIDATED);
    }
    @Override public TradePlan markReadyToExecute(
            TradePlanId id, TradePlanVersion validatedVersion) {
        exactLatest(id, validatedVersion, TradePlanStatus.RISK_VALIDATED);
        return service.transition(id, TradePlanStatus.READY_TO_EXECUTE);
    }
    @Override public TradePlan loadReadySnapshot(TradePlanId id, TradePlanVersion version) {
        return exact(id, version, TradePlanStatus.READY_TO_EXECUTE);
    }
    @Override public TradePlan recordExecuted(
            TradePlanId id, TradePlanVersion readyVersion) {
        exactLatest(id, readyVersion, TradePlanStatus.READY_TO_EXECUTE);
        return service.transition(id, TradePlanStatus.EXECUTED);
    }
    private TradePlan exact(
            TradePlanId id, TradePlanVersion version, TradePlanStatus status) {
        TradePlan plan = repository.find(id, version)
                .orElseThrow(() -> new IllegalArgumentException("TradePlan snapshot not found"));
        if (plan.status() != status) throw new IllegalStateException("Unexpected snapshot status");
        return plan;
    }
    private void exactLatest(
            TradePlanId id, TradePlanVersion version, TradePlanStatus status) {
        TradePlan latest = repository.findLatest(id)
                .orElseThrow(() -> new IllegalArgumentException("TradePlan not found"));
        if (!latest.version().equals(version) || latest.status() != status) {
            throw new IllegalStateException("Validation applies only to the latest exact version");
        }
    }
}
