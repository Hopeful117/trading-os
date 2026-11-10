package com.hope.trading.broker_service.ctrader.validation;

import com.hope.trading.broker_service.ctrader.execution.CTraderExecutionInstrumentContract;
import com.hope.trading.broker_service.ctrader.metadata.CTraderInstrumentMetadata;

/** Authenticated, read-only cTrader validation result for the account owner. */
public record CTraderReadOnlyValidation(
        CTraderExecutionInstrumentContract contract,
        CTraderInstrumentMetadata metadata) {
    public CTraderReadOnlyValidation {
        if (contract == null || metadata == null) {
            throw new IllegalArgumentException("cTrader validation result is required");
        }
    }
}
