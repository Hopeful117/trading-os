package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeDefinitionRepository extends JpaRepository<ChallengeDefinition, ChallengeDefinitionKey> {
}
