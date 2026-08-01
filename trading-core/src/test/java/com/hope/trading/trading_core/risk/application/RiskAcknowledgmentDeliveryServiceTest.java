package com.hope.trading.trading_core.risk.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskAcknowledgmentDeliveryServiceTest {
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void failedDeliveryIsDurablyReleasedAndExplicitRetryAcceptsIdempotentRemoteOutcome() {
        RiskPersistence persistence = mock(RiskPersistence.class);
        TradePlanRiskPort plans = mock(TradePlanRiskPort.class);
        UUID evaluationId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID firstToken = UUID.randomUUID();
        UUID retryToken = UUID.randomUUID();
        var first = new RiskPersistence.AcknowledgmentDelivery(evaluationId, planId, 4,
                "APPROVED", now.minusSeconds(1), firstToken);
        var retry = new RiskPersistence.AcknowledgmentDelivery(evaluationId, planId, 4,
                "APPROVED", now.minusSeconds(1), retryToken);
        when(persistence.claimAcknowledgment(evaluationId, now, true))
                .thenReturn(Optional.of(first), Optional.of(retry));
        doThrow(new IllegalStateException("response lost after remote commit")).doNothing()
                .when(plans).acknowledge(planId, 4, evaluationId, "APPROVED", now.minusSeconds(1));
        var service = new RiskAcknowledgmentDeliveryService(persistence, plans, Clock.fixed(now, ZoneOffset.UTC));

        service.deliver(evaluationId);
        service.deliver(evaluationId);

        verify(plans, times(2)).acknowledge(planId, 4, evaluationId, "APPROVED", now.minusSeconds(1));
        verify(persistence).acknowledgmentFailed(evaluationId, firstToken, now,
                "response lost after remote commit");
        verify(persistence).acknowledgmentDelivered(evaluationId, retryToken, now);
        var order = inOrder(persistence, plans);
        order.verify(persistence).claimAcknowledgment(evaluationId, now, true);
        order.verify(plans).acknowledge(planId, 4, evaluationId, "APPROVED", now.minusSeconds(1));
        order.verify(persistence).acknowledgmentFailed(evaluationId, firstToken, now,
                "response lost after remote commit");
    }
}
