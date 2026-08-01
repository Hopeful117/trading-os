package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.RiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationAcknowledgment;
import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationDecision;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskHandoffException;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRiskValidationAcknowledgmentRepository
        implements RiskValidationAcknowledgmentRepository {
    private final SpringDataRiskValidationAcknowledgmentRepository repository;

    public JpaRiskValidationAcknowledgmentRepository(
            SpringDataRiskValidationAcknowledgmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RiskValidationAcknowledgment> find(
            TradePlanId tradePlanId, TradePlanVersion acceptedVersion) {
        return repository.findByTradePlanIdAndAcceptedTradePlanVersion(
                tradePlanId.value(), acceptedVersion.value()).map(this::toDomain);
    }

    @Override
    public Optional<RiskValidationAcknowledgment> findByEvaluationId(UUID evaluationId) {
        return repository.findByEvaluationId(evaluationId).map(this::toDomain);
    }

    @Override
    public RiskValidationAcknowledgment save(RiskValidationAcknowledgment acknowledgment) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(acknowledgment)));
        } catch (DataIntegrityViolationException exception) {
            throw TradePlanRiskHandoffException.conflict(
                    "RISK_VALIDATION_ACKNOWLEDGMENT_CONFLICT",
                    "The Trade Plan version or evaluation is already linked");
        }
    }

    private JpaRiskValidationAcknowledgmentEntity toEntity(
            RiskValidationAcknowledgment acknowledgment) {
        JpaRiskValidationAcknowledgmentEntity entity = new JpaRiskValidationAcknowledgmentEntity();
        entity.acknowledgmentId = acknowledgment.acknowledgmentId();
        entity.tradePlanId = acknowledgment.tradePlanId();
        entity.acceptedTradePlanVersion = acknowledgment.acceptedTradePlanVersion();
        entity.riskValidatedTradePlanVersion = acknowledgment.riskValidatedTradePlanVersion();
        entity.tradingContextId = acknowledgment.tradingContextId();
        entity.tradingContextVersion = acknowledgment.tradingContextVersion();
        entity.evaluationId = acknowledgment.evaluationId();
        entity.decision = acknowledgment.decision().name();
        entity.evaluatedAt = acknowledgment.evaluatedAt();
        entity.acknowledgedAt = acknowledgment.acknowledgedAt();
        return entity;
    }

    private RiskValidationAcknowledgment toDomain(
            JpaRiskValidationAcknowledgmentEntity entity) {
        return new RiskValidationAcknowledgment(
                entity.acknowledgmentId, entity.tradePlanId, entity.acceptedTradePlanVersion,
                entity.riskValidatedTradePlanVersion, entity.tradingContextId,
                entity.tradingContextVersion, entity.evaluationId,
                RiskValidationDecision.valueOf(entity.decision), entity.evaluatedAt,
                entity.acknowledgedAt);
    }
}
