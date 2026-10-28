package com.hope.trading.market_data.exception;

public final class MarketDataProviderException extends RuntimeException {
    public static final String PROVIDER_TIMEOUT = "PROVIDER_TIMEOUT";

    private final String code;

    public MarketDataProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MarketDataProviderException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
