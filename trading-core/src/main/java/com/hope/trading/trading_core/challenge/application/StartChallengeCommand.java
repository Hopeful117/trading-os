package com.hope.trading.trading_core.challenge.application;

import java.util.UUID;

public record StartChallengeCommand(UUID ownerId, UUID accountId, UUID definitionId, String definitionVersion) {
}
