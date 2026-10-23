package com.hope.trading.broker_service.ctrader.authentication;

import jakarta.validation.constraints.NotBlank;

public record CTraderBindAccountRequest(@NotBlank String externalAccountId) {
}
