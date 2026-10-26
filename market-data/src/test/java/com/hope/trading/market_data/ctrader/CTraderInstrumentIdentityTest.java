package com.hope.trading.market_data.ctrader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CTraderInstrumentIdentityTest {
    @Test
    void preservesEnvironmentAndOpaqueProviderIdInProvenance() {
        var identity = new CTraderInstrumentIdentity(42, "EURUSD", "demo");
        assertEquals("cTrader:demo:42:EURUSD", identity.provenance());
    }

    @Test
    void rejectsIncompleteProviderIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new CTraderInstrumentIdentity(0, "EURUSD", "demo"));
        assertThrows(IllegalArgumentException.class, () -> new CTraderInstrumentIdentity(42, "", "demo"));
    }
}
