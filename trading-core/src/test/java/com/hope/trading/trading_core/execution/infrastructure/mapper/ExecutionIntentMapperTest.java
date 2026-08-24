package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.persistence.ExecutionIntentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionIntentMapperTest {

    private final ExecutionIntentMapper mapper = new ExecutionIntentMapper();

    private static ExecutionIntent buildIntent(UUID activeAttemptId) {
        return ExecutionIntent.rehydrate(
                new ExecutionIntentId(UUID.randomUUID()),
                new TradePlanReference(UUID.randomUUID(), 3L),
                new RiskApprovalReference(UUID.randomUUID(),
                        RiskApprovalReference.Decision.APPROVED, Instant.parse("2026-01-15T10:00:00Z")),
                new IdempotencyKey("idem-key-001"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ExecutionParameters("BTC/USD",
                        ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.LIMIT,
                        new BigDecimal("0.5"),
                        new BigDecimal("65000")),
                ExecutionStatus.VALIDATED,
                activeAttemptId == null ? null : new ExecutionAttemptId(activeAttemptId),
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T09:30:00Z"),
                Instant.parse("2026-01-15T12:00:00Z"),
                2L);
    }

    @Test
    void toEntityTransfersAllFields() {
        ExecutionIntent intent = buildIntent(null);

        ExecutionIntentEntity entity = mapper.toEntity(intent, new ExecutionIntentEntity());

        assertThat(entity.id).isEqualTo(intent.id().value());
        assertThat(entity.tradePlanId).isEqualTo(intent.tradePlan().tradePlanId());
        assertThat(entity.tradePlanVersion).isEqualTo(intent.tradePlan().version());
        assertThat(entity.riskEvaluationId).isEqualTo(intent.riskApproval().evaluationId());
        assertThat(entity.riskDecision).isEqualTo("APPROVED");
        assertThat(entity.riskApprovedAt).isEqualTo(intent.riskApproval().approvedAt());
        assertThat(entity.idempotencyKey).isEqualTo("idem-key-001");
        assertThat(entity.initiatorId).isEqualTo(intent.initiatorId());
        assertThat(entity.brokerAccountId).isEqualTo(intent.brokerAccountId());
        assertThat(entity.instrument).isEqualTo("BTC/USD");
        assertThat(entity.side).isEqualTo("BUY");
        assertThat(entity.orderType).isEqualTo("LIMIT");
        assertThat(entity.quantity).isEqualByComparingTo("0.5");
        assertThat(entity.limitPrice).isEqualByComparingTo("65000");
        assertThat(entity.status).isEqualTo("VALIDATED");
        assertThat(entity.activeAttemptId).isNull();
        assertThat(entity.createdAt).isEqualTo(Instant.parse("2026-01-15T09:00:00Z"));
        assertThat(entity.updatedAt).isEqualTo(Instant.parse("2026-01-15T09:30:00Z"));
        assertThat(entity.expiresAt).isEqualTo(Instant.parse("2026-01-15T12:00:00Z"));
    }

    @Test
    void toEntitySetsActiveAttemptIdWhenPresent() {
        UUID attemptId = UUID.randomUUID();
        ExecutionIntent intent = buildIntent(attemptId);

        ExecutionIntentEntity entity = mapper.toEntity(intent, new ExecutionIntentEntity());

        assertThat(entity.activeAttemptId).isEqualTo(attemptId);
    }

    @Test
    void toDomainRehydratesAllFields() {
        ExecutionIntentEntity entity = new ExecutionIntentEntity();
        entity.id = UUID.randomUUID();
        entity.tradePlanId = UUID.randomUUID();
        entity.tradePlanVersion = 5L;
        entity.riskEvaluationId = UUID.randomUUID();
        entity.riskDecision = "APPROVED_WITH_WARNINGS";
        entity.riskApprovedAt = Instant.parse("2026-02-01T08:00:00Z");
        entity.idempotencyKey = "key-123";
        entity.initiatorId = UUID.randomUUID();
        entity.brokerAccountId = UUID.randomUUID();
        entity.instrument = "ETH/USD";
        entity.side = "SELL";
        entity.orderType = "MARKET";
        entity.quantity = new BigDecimal("10.0");
        entity.limitPrice = null;
        entity.status = "CREATED";
        entity.activeAttemptId = null;
        entity.createdAt = Instant.parse("2026-02-01T07:00:00Z");
        entity.updatedAt = Instant.parse("2026-02-01T07:05:00Z");
        entity.expiresAt = Instant.parse("2026-02-01T10:00:00Z");
        entity.version = 1L;

        ExecutionIntent intent = mapper.toDomain(entity);

        assertThat(intent.id().value()).isEqualTo(entity.id);
        assertThat(intent.tradePlan().tradePlanId()).isEqualTo(entity.tradePlanId);
        assertThat(intent.tradePlan().version()).isEqualTo(5L);
        assertThat(intent.riskApproval().evaluationId()).isEqualTo(entity.riskEvaluationId);
        assertThat(intent.riskApproval().decision()).isEqualTo(RiskApprovalReference.Decision.APPROVED_WITH_WARNINGS);
        assertThat(intent.riskApproval().approvedAt()).isEqualTo(entity.riskApprovedAt);
        assertThat(intent.idempotencyKey().value()).isEqualTo("key-123");
        assertThat(intent.initiatorId()).isEqualTo(entity.initiatorId);
        assertThat(intent.brokerAccountId()).isEqualTo(entity.brokerAccountId);
        assertThat(intent.parameters().instrument()).isEqualTo("ETH/USD");
        assertThat(intent.parameters().side()).isEqualTo(ExecutionParameters.Side.SELL);
        assertThat(intent.parameters().orderType()).isEqualTo(ExecutionParameters.OrderType.MARKET);
        assertThat(intent.parameters().quantity()).isEqualByComparingTo("10.0");
        assertThat(intent.parameters().limitPrice()).isNull();
        assertThat(intent.status()).isEqualTo(ExecutionStatus.CREATED);
        assertThat(intent.activeAttemptId()).isEmpty();
        assertThat(intent.createdAt()).isEqualTo(entity.createdAt);
        assertThat(intent.updatedAt()).isEqualTo(entity.updatedAt);
        assertThat(intent.expiresAt()).isEqualTo(entity.expiresAt);
        assertThat(intent.version()).isEqualTo(1L);
    }

    @Test
    void toDomainPreservesActiveAttemptId() {
        ExecutionIntentEntity entity = new ExecutionIntentEntity();
        entity.id = UUID.randomUUID();
        entity.tradePlanId = UUID.randomUUID();
        entity.tradePlanVersion = 1L;
        entity.riskEvaluationId = UUID.randomUUID();
        entity.riskDecision = "APPROVED";
        entity.riskApprovedAt = Instant.parse("2026-03-01T00:00:00Z");
        entity.idempotencyKey = "k";
        entity.initiatorId = UUID.randomUUID();
        entity.brokerAccountId = UUID.randomUUID();
        entity.instrument = "SOL/USD";
        entity.side = "BUY";
        entity.orderType = "LIMIT";
        entity.quantity = new BigDecimal("100");
        entity.limitPrice = new BigDecimal("150");
        entity.status = "SUBMISSION_IN_PROGRESS";
        entity.activeAttemptId = UUID.randomUUID();
        entity.createdAt = Instant.parse("2026-03-01T00:00:00Z");
        entity.updatedAt = Instant.parse("2026-03-01T00:01:00Z");
        entity.expiresAt = Instant.parse("2026-03-01T06:00:00Z");
        entity.version = 4L;

        ExecutionIntent intent = mapper.toDomain(entity);

        assertThat(intent.activeAttemptId()).isPresent();
        assertThat(intent.activeAttemptId().get().value()).isEqualTo(entity.activeAttemptId);
    }

    @Test
    void enumRoundtripPreservesAllValues() {
        for (ExecutionParameters.Side side : ExecutionParameters.Side.values()) {
            for (ExecutionParameters.OrderType orderType : ExecutionParameters.OrderType.values()) {
                ExecutionIntentEntity entity = new ExecutionIntentEntity();
                entity.id = UUID.randomUUID();
                entity.tradePlanId = UUID.randomUUID();
                entity.tradePlanVersion = 1L;
                entity.riskEvaluationId = UUID.randomUUID();
                entity.riskDecision = "APPROVED";
                entity.riskApprovedAt = Instant.EPOCH;
                entity.idempotencyKey = "k";
                entity.initiatorId = UUID.randomUUID();
                entity.brokerAccountId = UUID.randomUUID();
                entity.instrument = "X/USD";
                entity.side = side.name();
                entity.orderType = orderType.name();
                entity.quantity = BigDecimal.ONE;
                entity.limitPrice = orderType == ExecutionParameters.OrderType.LIMIT ? BigDecimal.ONE : null;
                entity.status = ExecutionStatus.CREATED.name();
                entity.createdAt = Instant.EPOCH;
                entity.updatedAt = Instant.EPOCH;
                entity.expiresAt = Instant.EPOCH.plusSeconds(3600);
                entity.version = 0;

                ExecutionIntent intent = mapper.toDomain(entity);
                ExecutionIntentEntity roundtripped = mapper.toEntity(intent, new ExecutionIntentEntity());

                assertThat(roundtripped.side).isEqualTo(side.name());
                assertThat(roundtripped.orderType).isEqualTo(orderType.name());
                assertThat(roundtripped.status).isEqualTo(ExecutionStatus.CREATED.name());
            }
        }
    }

    @Test
    void toEntityMutatesProvidedTargetInstance() {
        ExecutionIntent intent = buildIntent(null);
        ExecutionIntentEntity target = new ExecutionIntentEntity();
        target.id = UUID.randomUUID();

        ExecutionIntentEntity result = mapper.toEntity(intent, target);

        assertThat(result).isSameAs(target);
        assertThat(result.id).isEqualTo(intent.id().value());
    }
}
