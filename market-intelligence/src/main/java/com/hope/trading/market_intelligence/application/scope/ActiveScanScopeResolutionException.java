package com.hope.trading.market_intelligence.application.scope;

public final class ActiveScanScopeResolutionException extends RuntimeException {
    private final String code;
    private final int status;

    private ActiveScanScopeResolutionException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ActiveScanScopeResolutionException notFound(String message) {
        return new ActiveScanScopeResolutionException(
                "ACTIVE_SCAN_ACCOUNT_NOT_FOUND_OR_FORBIDDEN", 404, message);
    }

    public static ActiveScanScopeResolutionException unavailable(String message) {
        return new ActiveScanScopeResolutionException(
                "ACTIVE_SCAN_SCOPE_UNAVAILABLE", 503, message);
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
