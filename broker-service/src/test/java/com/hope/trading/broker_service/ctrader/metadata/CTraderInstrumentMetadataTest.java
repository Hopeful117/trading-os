package com.hope.trading.broker_service.ctrader.metadata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CTraderInstrumentMetadataTest {
    @Test
    void preservesProviderFactsAndUnitsWithoutClassification() {
        var account = new CTraderInstrumentMetadata.CTraderAccountFacts(
                "49289512", false, "HEDGED", "USD", new BigDecimal("50000"),
                new BigDecimal("50000"), null, null, 10000L, null, 2, "MARKET");
        var symbol = new CTraderInstrumentMetadata.CTraderSymbolFacts(
                1, "EURUSD", "Euro vs US Dollar", 7L, "EUR", "USD", 5, 4,
                10000000L, 100000L, 10000000000L, 100000L, 25L, "PER_LOT",
                null, null, -3L, -1L, "PIPS", 5, "ENABLED", "units",
                0L, 0L, "POINTS");

        var metadata = new CTraderInstrumentMetadata(account, symbol);

        assertThat(metadata.symbol().swapLong()).isEqualTo(-3L);
        assertThat(metadata.symbol().minCommission()).isNull();
        assertThat(metadata.account().margin()).isNull();
    }

    @Test
    void rejectsMissingFactsRequiredForIdentity() {
        var account = new CTraderInstrumentMetadata.CTraderAccountFacts(
                "49289512", null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> new CTraderInstrumentMetadata(account,
                new CTraderInstrumentMetadata.CTraderSymbolFacts(0, "EURUSD", null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cTrader symbol identity is required");
    }
}
