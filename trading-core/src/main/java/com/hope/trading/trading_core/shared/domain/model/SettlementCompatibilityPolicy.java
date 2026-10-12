package com.hope.trading.trading_core.shared.domain.model;

public final class SettlementCompatibilityPolicy {
    private SettlementCompatibilityPolicy() {
    }

    public static boolean isCompatible(SettlementModel settlementModel,
                                       InstrumentProductType productType) {
        return settlementModel == SettlementModel.INVENTORY && productType == InstrumentProductType.SPOT
                || settlementModel == SettlementModel.CASH
                && productType == InstrumentProductType.LINEAR_PERPETUAL;
    }

    public static void requireCompatible(SettlementModel settlementModel,
                                         InstrumentProductType productType) {
        if (!isCompatible(settlementModel, productType)) {
            throw new IllegalArgumentException("Settlement model is incompatible with instrument product type");
        }
    }
}
