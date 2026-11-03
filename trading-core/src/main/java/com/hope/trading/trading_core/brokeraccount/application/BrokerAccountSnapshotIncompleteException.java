package com.hope.trading.trading_core.brokeraccount.application;

import java.util.List;

public class BrokerAccountSnapshotIncompleteException extends RuntimeException {
    private final String completeness;
    private final String freshness;
    private final List<String> reasons;

    public BrokerAccountSnapshotIncompleteException(String completeness, String freshness,
                                                    List<String> reasons) {
        super("Broker account snapshot is not complete and fresh");
        this.completeness = completeness;
        this.freshness = freshness;
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public String completeness() {
        return completeness;
    }

    public String freshness() {
        return freshness;
    }

    public List<String> reasons() {
        return reasons;
    }
}
