package com.hope.trading.trading_core.challenge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartChallengeRequest(
        @NotNull UUID definitionId,
        @NotBlank String definitionVersion
) {
}
