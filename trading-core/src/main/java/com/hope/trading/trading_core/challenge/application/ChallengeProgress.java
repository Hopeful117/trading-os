package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read projection; changing progress is not persisted as a second source of truth. */
public record ChallengeProgress(
        UUID challengeId,
        UUID accountId,
        ChallengeStatus status,
        BigDecimal startingCapital,
        BigDecimal currentValue,
        BigDecimal targetValue,
        BigDecimal currentProfit,
        BigDecimal targetProgress,
        BigDecimal dailyReferenceBalance,
        BigDecimal dailyLossRemaining,
        BigDecimal totalDrawdownRemaining,
        Instant evaluatedAt
) { }
