package com.hope.trading.trading_core.challenge.application;

public class ChallengeNotStartedException extends ChallengeNotFoundException {
    public ChallengeNotStartedException(String message) {
        super(message);
    }
}
