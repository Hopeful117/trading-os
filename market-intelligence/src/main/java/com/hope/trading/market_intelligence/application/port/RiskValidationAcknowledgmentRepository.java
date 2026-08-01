package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationAcknowledgment;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import java.util.Optional;
import java.util.UUID;

public interface RiskValidationAcknowledgmentRepository {
    Optional<RiskValidationAcknowledgment> find(
            TradePlanId tradePlanId, TradePlanVersion acceptedVersion);
    Optional<RiskValidationAcknowledgment> findByEvaluationId(UUID evaluationId);
    RiskValidationAcknowledgment save(RiskValidationAcknowledgment acknowledgment);
}
