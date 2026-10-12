package com.hope.trading.trading_core.shared.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementCompatibilityPolicyTest {
    @Test
    void allowsInventorySpotAndCashPerpetualCombinations() {
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.INVENTORY,
                InstrumentProductType.SPOT)).isTrue();
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.CASH,
                InstrumentProductType.LINEAR_PERPETUAL)).isTrue();
    }

    @Test
    void rejectsUnsupportedAndUnknownCombinations() {
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.INVENTORY,
                InstrumentProductType.LINEAR_PERPETUAL)).isFalse();
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.CASH,
                InstrumentProductType.SPOT)).isFalse();
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.UNKNOWN,
                InstrumentProductType.SPOT)).isFalse();
        assertThatThrownBy(() -> SettlementCompatibilityPolicy.requireCompatible(
                SettlementModel.UNKNOWN, InstrumentProductType.LINEAR_PERPETUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
