package com.hope.trading.market_data.kraken.websocket;

public enum KrakenStreamTerminationClassification {
    REMOTE_CLOSE,
    NETWORK_FAILURE,
    LOCAL_DISPOSE,
    APPLICATION_SHUTDOWN,
    EXPLICIT_UNSUBSCRIBE,
    UNKNOWN_TRANSPORT_COMPLETION
}
