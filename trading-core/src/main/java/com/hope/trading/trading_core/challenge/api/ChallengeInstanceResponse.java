package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChallengeInstanceResponse(
        UUID id,
        UUID accountId,
        UUID definitionId,
        String definitionVersion,
        UUID riskPolicyId,
        String riskPolicyVersion,
        BigDecimal startingCapital,
        String capitalCurrency,
        Instant startedAt,
        ChallengeStatus status,
        Instant passedAt,
        Instant breachedAt,
        String terminalReason,
        UUID terminalRiskEvaluationId
) {
    public static ChallengeInstanceResponse from(ChallengeInstance challenge) {
        return new ChallengeInstanceResponse(challenge.id(), challenge.accountId(), challenge.definitionId(),
                challenge.definitionVersion(), challenge.riskPolicyId(), challenge.riskPolicyVersion(),
                challenge.startingCapital(), challenge.capitalCurrency(), challenge.startedAt(),
                challenge.status(), challenge.passedAt(), challenge.breachedAt(), challenge.terminalReason(),
                challenge.terminalRiskEvaluationId());
    }
}
