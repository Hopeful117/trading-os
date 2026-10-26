package com.hope.trading.market_data.ctrader.authentication;

public final class CTraderTokenRefreshException extends IllegalStateException {
    private final boolean confirmedInvalid;

    public CTraderTokenRefreshException(String message, boolean confirmedInvalid) {
        super(message);
        this.confirmedInvalid = confirmedInvalid;
    }

    public boolean confirmedInvalid() { return confirmedInvalid; }
}
