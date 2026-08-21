package com.hope.trading.market_intelligence.strategy.domain;

public class MissingSemanticInputException extends RuntimeException {

    private final RequiredSemanticInput input;

    public MissingSemanticInputException(RequiredSemanticInput input) {
        super("Required semantic input is missing: " + input);
        this.input = input;
    }

    public RequiredSemanticInput input() {
        return input;
    }
}
