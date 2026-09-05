package com.hope.trading.trading_core.positionclose.domain.model;

public enum PositionCloseStatus {
    CREATED,
    SUBMITTED,
    ACKNOWLEDGED,
    REJECTED,
    UNKNOWN,
    CLOSED,
    NOT_SUBMITTED
}