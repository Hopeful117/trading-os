package com.hope.trading.trading_core.shared.domain.model;

public final class SettlementCompatibilityPolicy {
    private SettlementCompatibilityPolicy() {
    }

    public static boolean isCompatible(SettlementModel settlementModel,
                                       InstrumentProductType productType) {
        if (settlementModel == null || productType == null) {
            return false;
        }
        return switch (settlementModel) {
            case INVENTORY -> productType == InstrumentProductType.SPOT;
            case CASH -> productType == InstrumentProductType.LINEAR_PERPETUAL;
            case UNKNOWN -> false;
        };
    }

    public static void requireCompatible(SettlementModel settlementModel,
                                         InstrumentProductType productType) {
        if (!isCompatible(settlementModel, productType)) {
            throw new IllegalArgumentException("Settlement model is incompatible with instrument product type");
        }
    }
}
