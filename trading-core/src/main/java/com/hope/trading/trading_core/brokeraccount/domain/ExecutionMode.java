package com.hope.trading.trading_core.brokeraccount.domain;

/**
 * Selects the Trading OS execution boundary, not the provider's economic
 * environment or whether an account represents real money.
 */
public enum ExecutionMode {
    /**
     * Uses the external broker/provider boundary for authoritative facts and
     * potential execution. This can still represent an economically simulated
     * provider account, and it does not authorize execution by itself.
     */
    LIVE,

    /**
     * Keeps orders, fills, settlement, and position lifecycle inside Trading
     * OS. No trading instruction may be sent to an external provider.
     */
    PAPER
}
