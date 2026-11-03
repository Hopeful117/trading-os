package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;

public record BrokerAccountReadOnlyStatusResponse(
        String status,
        boolean executionAllowed,
        boolean riskEvaluationAllowed,
        BrokerAccountSnapshotV2Dto snapshot) {
}
