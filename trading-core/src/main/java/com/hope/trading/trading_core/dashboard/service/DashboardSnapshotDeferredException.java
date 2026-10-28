package com.hope.trading.trading_core.dashboard.service;

/** The dashboard cannot refresh because the broker snapshot safety window is active. */
public final class DashboardSnapshotDeferredException extends RuntimeException {
    public DashboardSnapshotDeferredException() {
        super("Broker authoritative snapshot is deferred by broker safety policy");
    }
}
