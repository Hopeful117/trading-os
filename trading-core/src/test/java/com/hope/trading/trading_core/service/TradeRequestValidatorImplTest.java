package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STORY-0020A: a trade request missing any mandatory field must be rejected
 * before reaching persistence or risk evaluation.
 */
class TradeRequestValidatorImplTest {

    private final TradeRequestValidatorImpl validator = new TradeRequestValidatorImpl();

    private TradeRequest validRequest() {
        TradeRequest request = new TradeRequest();
        request.setAccountId(UUID.randomUUID());
        request.setSymbol("BTC/USD");
        request.setQuantity(BigDecimal.ONE);
        request.setStopLoss(BigDecimal.TEN);
        request.setTakeProfit(BigDecimal.valueOf(20));
        return request;
    }

    @Test
    void validRequestPasses() {
        assertThatCode(() -> validator.validate(validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void nullRequestIsRejected() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void eachMissingMandatoryFieldIsRejected() {
        for (String field : new String[] {"AccountId", "Quantity", "StopLoss", "TakeProfit"}) {
            TradeRequest request = validRequest();
            switch (field) {
                case "AccountId" -> request.setAccountId(null);
                case "Quantity" -> request.setQuantity(null);
                case "StopLoss" -> request.setStopLoss(null);
                case "TakeProfit" -> request.setTakeProfit(null);
                default -> throw new IllegalStateException(field);
            }
            assertThatThrownBy(() -> validator.validate(request))
                    .as("missing %s must be rejected", field)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(field);
        }
    }

    @Test
    void blankSymbolIsRejected() {
        TradeRequest request = validRequest();
        request.setSymbol("   ");
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbol");
    }
}
