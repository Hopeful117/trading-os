package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.RiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationAcknowledgment;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryRiskValidationAcknowledgmentRepository
        implements RiskValidationAcknowledgmentRepository {
    private final Map<PlanVersion, RiskValidationAcknowledgment> byPlanVersion = new HashMap<>();
    private final Map<UUID, RiskValidationAcknowledgment> byEvaluation = new HashMap<>();

    @Override public synchronized Optional<RiskValidationAcknowledgment> find(
            TradePlanId tradePlanId, TradePlanVersion acceptedVersion) {
        return Optional.ofNullable(byPlanVersion.get(
                new PlanVersion(tradePlanId.value(), acceptedVersion.value())));
    }

    @Override public synchronized Optional<RiskValidationAcknowledgment> findByEvaluationId(
            UUID evaluationId) {
        return Optional.ofNullable(byEvaluation.get(evaluationId));
    }

    @Override public synchronized RiskValidationAcknowledgment save(
            RiskValidationAcknowledgment acknowledgment) {
        PlanVersion key = new PlanVersion(
                acknowledgment.tradePlanId(), acknowledgment.acceptedTradePlanVersion());
        if (byPlanVersion.containsKey(key) || byEvaluation.containsKey(acknowledgment.evaluationId())) {
            throw new IllegalStateException("Risk validation acknowledgment already exists");
        }
        byPlanVersion.put(key, acknowledgment);
        byEvaluation.put(acknowledgment.evaluationId(), acknowledgment);
        return acknowledgment;
    }

    private record PlanVersion(UUID tradePlanId, long version) { }
}
