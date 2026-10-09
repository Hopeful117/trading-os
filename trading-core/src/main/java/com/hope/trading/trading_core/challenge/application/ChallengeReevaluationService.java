package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.AccountRiskMonitoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Coordinates one post-settlement evaluation without owning Risk calculations. */
@Service
public class ChallengeReevaluationService {
    private final AccountRepository accounts;
    private final ChallengeDefinitionRepository definitions;
    private final ChallengeInstanceRepository instances;
    private final AccountRiskMonitoringService monitoring;
    private final ChallengeEvaluator evaluator;
    private final Clock clock;

    public ChallengeReevaluationService(AccountRepository accounts,
                                        ChallengeDefinitionRepository definitions,
                                        ChallengeInstanceRepository instances,
                                        AccountRiskMonitoringService monitoring,
                                        ChallengeEvaluator evaluator,
                                        Clock clock) {
        this.accounts = accounts;
        this.definitions = definitions;
        this.instances = instances;
        this.monitoring = monitoring;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reevaluate(Account account) {
        ChallengeInstance challenge = instances.findByAccountIdAndStatus(account.getAccountId(), ChallengeStatus.ACTIVE)
                .orElse(null);
        if (challenge == null) return;
        ChallengeDefinition definition = definitions.findById(
                        new ChallengeDefinitionKey(challenge.definitionId(), challenge.definitionVersion()))
                .orElseThrow(() -> new IllegalStateException("Challenge definition does not exist"));
        AccountRiskMonitoringService.Evaluation evaluation = monitoring.evaluate(challenge, account, clock.instant());
        ChallengeStatus before = challenge.status();
        evaluator.evaluate(challenge, definition, account, evaluation.result(), evaluation.evaluationId(),
                evaluation.result().evaluatedAt());
        if (before != challenge.status()) instances.save(challenge);
    }
}
