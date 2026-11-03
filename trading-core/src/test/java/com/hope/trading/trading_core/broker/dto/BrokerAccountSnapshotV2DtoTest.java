package com.hope.trading.trading_core.broker.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerAccountSnapshotV2DtoTest {
    @Test
    void legacyPayloadDefaultsOnlyTheAdditiveFields() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();

        var snapshot = mapper.readValue("""
                {
                  "brokerAccountId":"00000000-0000-0000-0000-000000000001",
                  "providerRegistrationReference":"00000000-0000-0000-0000-000000000002",
                  "externalAccountId":"legacy",
                  "balances":{"USD":1000},
                  "positions":[],
                  "observedAt":"2026-11-03T12:00:00Z",
                  "account":{"valuationAsset":"USD","balance":1000,"equity":null,"usedMargin":null,"freeMargin":null},
                  "orders":[],
                  "deals":[],
                  "completeness":"INCOMPLETE",
                  "freshness":"FRESH",
                  "observationStartedAt":"2026-11-03T12:00:00Z",
                  "completedAt":"2026-11-03T12:00:00Z",
                  "unavailabilityReasons":["EQUITY_UNAVAILABLE"]
                }
                """, BrokerAccountSnapshotV2Dto.class);

        assertThat(snapshot.positionPnls()).isEmpty();
        assertThat(snapshot.positionAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_EMPTY);
        assertThat(snapshot.orderAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_EMPTY);
        assertThat(snapshot.pnlAvailability()).isEqualTo(SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT);
        assertThat(snapshot.pnlComplete()).isFalse();
        assertThat(snapshot.account().positionModel()).isEqualTo(AccountPositionModel.UNKNOWN);
        assertThat(snapshot.account().marginCalculationModel()).isEqualTo(MarginCalculationModel.UNKNOWN);
    }
}
