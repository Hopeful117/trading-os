package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeProgressionEvidence;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.AccountRiskMonitoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Coordinates one post-settlement evaluation without owning Risk calculations. */
@Service
public class ChallengeReevaluationService {
    private final AccountRepository accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final ChallengeDefinitionRepository definitions;
    private final ChallengeInstanceRepository instances;
    private final AccountRiskMonitoringService monitoring;
    private final ChallengeEvaluator evaluator;
    private final ExecutedTradingEvidenceReader evidenceReader;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ChallengeReevaluationService(AccountRepository accounts,
                                        BrokerAccountRepository brokerAccounts,
                                        ChallengeDefinitionRepository definitions,
                                        ChallengeInstanceRepository instances,
                                        AccountRiskMonitoringService monitoring,
                                        ChallengeEvaluator evaluator,
                                        ExecutedTradingEvidenceReader evidenceReader,
                                        Clock clock) {
        this.accounts = accounts;
        this.brokerAccounts = brokerAccounts;
        this.definitions = definitions;
        this.instances = instances;
        this.monitoring = monitoring;
        this.evaluator = evaluator;
        this.evidenceReader = evidenceReader;
        this.clock = clock;
    }

    public ChallengeReevaluationService(AccountRepository accounts,
                                        BrokerAccountRepository brokerAccounts,
                                        ChallengeDefinitionRepository definitions,
                                        ChallengeInstanceRepository instances,
                                        AccountRiskMonitoringService monitoring,
                                        ChallengeEvaluator evaluator,
                                        Clock clock) {
        this(accounts, brokerAccounts, definitions, instances, monitoring, evaluator, null, clock);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reevaluate(Account account) {
        ChallengeInstance challenge = instances.findByAccountIdAndStatus(account.getAccountId(), ChallengeStatus.ACTIVE)
                .orElse(null);
        if (challenge == null) return;
        reevaluateLoaded(challenge, account, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reevaluate(Account account, ChallengeProgressionEvidence evidence) {
        ChallengeInstance challenge = instances.findByAccountIdAndStatus(account.getAccountId(), ChallengeStatus.ACTIVE)
                .orElse(null);
        if (challenge == null) return;
        reevaluateLoaded(challenge, account, evidence);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reevaluateAccount(UUID accountId) {
        ChallengeInstance challenge = instances.findByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)
                .orElse(null);
        if (challenge == null) return;
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null) return;
        if (account.getBrokerAccountId() == null
                || brokerAccounts.findById(account.getBrokerAccountId())
                .filter(broker -> broker.executionMode() == ExecutionMode.PAPER).isEmpty()) {
            return;
        }
        reevaluateLoaded(challenge, account, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ChallengeStatus reevaluate(UUID challengeId) {
        ChallengeInstance challenge = instances.findById(challengeId).orElse(null);
        if (challenge == null || challenge.status() != ChallengeStatus.ACTIVE) {
            return challenge == null ? null : challenge.status();
        }
        Account account = accounts.findById(challenge.accountId())
                .orElseThrow(() -> new IllegalStateException("Challenge Account does not exist"));
        if (account.getBrokerAccountId() == null
                || brokerAccounts.findById(account.getBrokerAccountId())
                .filter(broker -> broker.executionMode() == ExecutionMode.PAPER).isEmpty()) {
            return null;
        }
        return reevaluateLoaded(challenge, account, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ChallengeStatus reevaluate(UUID challengeId, ChallengeProgressionEvidence evidence) {
        ChallengeInstance challenge = instances.findById(challengeId).orElse(null);
        if (challenge == null || challenge.status() != ChallengeStatus.ACTIVE) {
            return challenge == null ? null : challenge.status();
        }
        Account account = accounts.findById(challenge.accountId())
                .orElseThrow(() -> new IllegalStateException("Challenge Account does not exist"));
        if (account.getBrokerAccountId() == null
                || brokerAccounts.findById(account.getBrokerAccountId())
                .filter(broker -> broker.executionMode() == ExecutionMode.PAPER).isEmpty()) {
            return null;
        }
        return reevaluateLoaded(challenge, account, evidence);
    }

    private ChallengeStatus reevaluateLoaded(ChallengeInstance challenge, Account account,
                                             ChallengeProgressionEvidence evidence) {
        ChallengeDefinition definition = definitions.findById(
                        new ChallengeDefinitionKey(challenge.definitionId(), challenge.definitionVersion()))
                .orElseThrow(() -> new IllegalStateException("Challenge definition does not exist"));
        AccountRiskMonitoringService.Evaluation evaluation = monitoring.evaluate(challenge, account, clock.instant());
        ChallengeProgressionEvidence effectiveEvidence = evidence == null
                ? evidenceFor(challenge, evaluation.result().evaluatedAt()) : evidence;
        ChallengeStatus before = challenge.status();
        evaluator.evaluate(challenge, definition, account, evaluation.result(), evaluation.evaluationId(),
                evaluation.result().evaluatedAt(), effectiveEvidence);
        if (before != challenge.status()
                || challenge.phaseProgressionState() != null) instances.save(challenge);
        return challenge.status();
    }

    private ChallengeProgressionEvidence evidenceFor(ChallengeInstance challenge, Instant observedAt) {
        if (evidenceReader == null) return ChallengeProgressionEvidence.unavailable(clock.instant());
        var executedTrading = evidenceReader.read(challenge, observedAt);
        return new ChallengeProgressionEvidence(executedTrading, false, "", observedAt);
    }
}
