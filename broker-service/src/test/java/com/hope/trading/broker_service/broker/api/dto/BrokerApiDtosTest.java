package com.hope.trading.broker_service.broker.api.dto;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class BrokerApiDtosTest {
    @Test
    void mapsExecutionOutcomes() {
        assertThat(BrokerApiDtos.BrokerApiResponse.from(new BrokerModels.Acknowledged("order", "corr")).outcome())
                .isEqualTo("ACKNOWLEDGED");
        assertThat(BrokerApiDtos.BrokerApiResponse.from(new BrokerModels.Rejected("order", "reason")).reasonCode())
                .isEqualTo("reason");
        assertThat(BrokerApiDtos.BrokerApiResponse.from(new BrokerModels.Unknown("unknown")).status())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void mapsReconciliationOutcomes() {
        assertThat(BrokerApiDtos.BrokerApiResponse.from(
                new BrokerModels.ReconciledOrder("order", "corr", BrokerModels.OrderStatus.FILLED)).status())
                .isEqualTo("FILLED");
        assertThat(BrokerApiDtos.BrokerApiResponse.from(new BrokerModels.ConfirmedAbsent()).outcome())
                .isEqualTo("ABSENT");
        assertThat(BrokerApiDtos.BrokerApiResponse.from(new BrokerModels.Inconsistent("conflict")).reasonCode())
                .isEqualTo("conflict");
    }

    @Test
    void convertsApiRequestsToDomainModels() {
        UUID intent = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        var execution = new BrokerApiDtos.ExecutionApiRequest(intent, attempt, "key", account,
                "EURUSD", BrokerModels.Side.BUY, BrokerModels.OrderType.MARKET, BigDecimal.ONE, null,
                BigDecimal.ONE, List.of(BigDecimal.TWO), BigDecimal.ONE, "OPEN", true, Instant.EPOCH);
        assertThat(execution.toModel().executionIntentId()).isEqualTo(intent);
        assertThat(new BrokerApiDtos.ReconcileApiRequest(intent, attempt, "key", account)
                .toModel().brokerAccountId()).isEqualTo(account);
    }
}
