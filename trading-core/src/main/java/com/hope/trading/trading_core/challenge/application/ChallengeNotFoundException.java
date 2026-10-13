package com.hope.trading.trading_core.challenge.application;

public class ChallengeNotFoundException extends IllegalArgumentException {
    public ChallengeNotFoundException(String message) {
        super(message);
    }
}
