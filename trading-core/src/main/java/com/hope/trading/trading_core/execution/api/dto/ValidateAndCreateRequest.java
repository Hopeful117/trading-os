package com.hope.trading.trading_core.execution.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Request body for the validation endpoint.
 *
 * <p>Contains only the minimal input needed to locate authoritative data.
 * Execution parameters, risk references, and account data are loaded from
 * persistence — never from the caller.
 */
public record ValidateAndCreateRequest(
        @NotNull UUID tradePlanId,
        @Positive long tradePlanVersion,
        @NotNull UUID evaluationId,
        @NotNull UUID brokerAccountId,
        @NotNull @Future Instant expiresAt
) {}
