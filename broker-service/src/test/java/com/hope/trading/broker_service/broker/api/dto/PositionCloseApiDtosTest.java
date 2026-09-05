package com.hope.trading.broker_service.broker.api.dto;

import com.hope.trading.broker_service.broker.api.dto.PositionCloseApiDtos.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionCloseApiDtosTest {

    @Test
    void resolveTargetRequestToModel() {
        UUID id = UUID.randomUUID();
        ResolveTargetApiRequest req = new ResolveTargetApiRequest(id, "ref-1");
        ResolveTargetRequest model = req.toModel();
        assertEquals(id, model.brokerAccountId());
        assertEquals("ref-1", model.brokerPositionReference());
    }

    @Test
    void resolvedTargetApiResponseFrom() {
        ResolvedPositionCloseTarget target = new ResolvedPositionCloseTarget(UUID.randomUUID(), "scope-1");
        ResolvedTargetApiResponse resp = ResolvedTargetApiResponse.from(target);
        assertEquals(target.brokerAccountId(), resp.brokerAccountId());
        assertEquals("scope-1", resp.resolvedMutationScope());
    }

    @Test
    void executeCloseRequestToModel() {
        UUID id = UUID.randomUUID();
        ExecuteCloseApiRequest req = new ExecuteCloseApiRequest(id, "scope-1", "key-1");
        ExecuteCloseRequest model = req.toModel();
        assertEquals(id, model.brokerAccountId());
        assertEquals("scope-1", model.resolvedMutationScope());
        assertEquals("key-1", model.idempotencyKey());
    }

    @Test
    void brokerCloseApiResponseFromAcknowledged() {
        CloseAcknowledged ack = new CloseAcknowledged("ext-1", "corr-1");
        BrokerCloseApiResponse resp = BrokerCloseApiResponse.from(ack);
        assertEquals("ACKNOWLEDGED", resp.outcome());
        assertEquals("ext-1", resp.externalOrderId());
        assertEquals("corr-1", resp.correlationId());
        assertEquals("ACKNOWLEDGED", resp.status());
        assertNull(resp.reasonCode());
    }

    @Test
    void brokerCloseApiResponseFromRejected() {
        CloseRejected rej = new CloseRejected("ext-1", "INSUFFICIENT_FUNDS");
        BrokerCloseApiResponse resp = BrokerCloseApiResponse.from(rej);
        assertEquals("REJECTED", resp.outcome());
        assertEquals("ext-1", resp.externalOrderId());
        assertEquals("REJECTED", resp.status());
        assertEquals("INSUFFICIENT_FUNDS", resp.reasonCode());
    }

    @Test
    void brokerCloseApiResponseFromUnknown() {
        CloseUnknown unk = new CloseUnknown("TIMEOUT");
        BrokerCloseApiResponse resp = BrokerCloseApiResponse.from(unk);
        assertEquals("UNKNOWN", resp.outcome());
        assertNull(resp.externalOrderId());
        assertEquals("UNKNOWN", resp.status());
        assertEquals("TIMEOUT", resp.reasonCode());
    }

    @Test
    void reconcileCloseRequestToModel() {
        UUID id = UUID.randomUUID();
        ReconcileCloseApiRequest req = new ReconcileCloseApiRequest(id, "scope-1", "key-1");
        ReconcileCloseRequest model = req.toModel();
        assertEquals(id, model.brokerAccountId());
        assertEquals("scope-1", model.resolvedMutationScope());
        assertEquals("key-1", model.idempotencyKey());
    }

    @Test
    void reconcileCloseApiResponseFromExposureAbsent() {
        ReconcileCloseApiResponse resp = ReconcileCloseApiResponse.from(new ExposureConfirmedAbsent());
        assertEquals("EXPOSURE_CONFIRMED_ABSENT", resp.outcome());
        assertEquals("EXPOSURE_CONFIRMED_ABSENT", resp.reconciliationResult());
    }

    @Test
    void reconcileCloseApiResponseFromCommandNotExecuted() {
        ReconcileCloseApiResponse resp = ReconcileCloseApiResponse.from(new CommandConfirmedNotExecuted());
        assertEquals("COMMAND_CONFIRMED_NOT_EXECUTED", resp.outcome());
        assertEquals("COMMAND_CONFIRMED_NOT_EXECUTED", resp.reconciliationResult());
    }

    @Test
    void reconcileCloseApiResponseFromInconclusive() {
        ReconcileCloseApiResponse resp = ReconcileCloseApiResponse.from(new Inconclusive("UNKNOWN_ERROR"));
        assertEquals("INCONCLUSIVE", resp.outcome());
        assertEquals("INCONCLUSIVE: UNKNOWN_ERROR", resp.reconciliationResult());
    }
}
