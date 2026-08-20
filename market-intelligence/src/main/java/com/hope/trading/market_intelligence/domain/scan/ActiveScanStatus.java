package com.hope.trading.market_intelligence.domain.scan;

public enum ActiveScanStatus {
    READY_TO_DISPATCH,
    DISPATCH_REQUESTED,
    RUNNING,
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED,
    COMPLETED_NO_WORK;

    public boolean isTerminal() {
        return switch (this) {
            case PARTIALLY_COMPLETED, COMPLETED, FAILED, COMPLETED_NO_WORK -> true;
            default -> false;
        };
    }

    public boolean canAdvanceTo(ActiveScanStatus target) {
        if (this == target) {
            return true;
        }
        if (isTerminal()) {
            return false;
        }
        return switch (this) {
            case READY_TO_DISPATCH -> switch (target) {
                case DISPATCH_REQUESTED, RUNNING, PARTIALLY_COMPLETED, COMPLETED, FAILED -> true;
                default -> false;
            };
            case DISPATCH_REQUESTED -> switch (target) {
                case RUNNING, PARTIALLY_COMPLETED, COMPLETED, FAILED -> true;
                default -> false;
            };
            case RUNNING -> switch (target) {
                case PARTIALLY_COMPLETED, COMPLETED, FAILED -> true;
                default -> false;
            };
            default -> false;
        };
    }

    public int progressionRank() {
        return switch (this) {
            case READY_TO_DISPATCH -> 0;
            case DISPATCH_REQUESTED -> 1;
            case RUNNING -> 2;
            case PARTIALLY_COMPLETED, COMPLETED, FAILED, COMPLETED_NO_WORK -> 3;
        };
    }
}
