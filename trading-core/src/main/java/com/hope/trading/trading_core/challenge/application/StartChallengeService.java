package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartChallengeService {
    private final AccountRepository accounts;
    private final ChallengeDefinitionRepository definitions;
    private final ChallengeInstanceRepository instances;
    private final RiskPersistence riskPersistence;
    private final Clock clock;

    public StartChallengeService(AccountRepository accounts, ChallengeDefinitionRepository definitions,
                                 ChallengeInstanceRepository instances, RiskPersistence riskPersistence,
                                 Clock clock) {
        this.accounts = accounts;
        this.definitions = definitions;
        this.instances = instances;
        this.riskPersistence = riskPersistence;
        this.clock = clock;
    }

    @Transactional
    public ChallengeInstance start(StartChallengeCommand command) {
        Objects.requireNonNull(command, "command");
        Account account = accounts.findById(command.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account does not exist"));
        if (account.getUser() == null || !command.ownerId().equals(account.getUser().getUserId())) {
            throw new IllegalArgumentException("Account is not owned by the caller");
        }
        if (instances.existsByAccountIdAndStatus(account.getAccountId(), ChallengeStatus.ACTIVE)) {
            throw new IllegalStateException("Account already has an ACTIVE Challenge");
        }

        ChallengeDefinition definition = definitions.findById(
                        new ChallengeDefinitionKey(command.definitionId(), command.definitionVersion()))
                .orElseThrow(() -> new IllegalArgumentException("Challenge definition does not exist"));
        if (riskPersistence.profile(definition.riskPolicyId(), definition.riskPolicyVersion()).isEmpty()) {
            throw new IllegalArgumentException("Challenge Risk Policy does not exist");
        }

        BigDecimal accountStartingBalance = account.getStartingBalance();
        if (accountStartingBalance == null || accountStartingBalance.signum() <= 0) {
            throw new IllegalArgumentException("Account starting balance must be positive");
        }
        if (!definition.capitalCurrency().equalsIgnoreCase(account.getBaseCurrency())) {
            throw new IllegalArgumentException("Challenge capital currency does not match Account currency");
        }
        if (accountStartingBalance.compareTo(definition.startingCapital()) != 0) {
            throw new IllegalArgumentException("Account starting balance does not match Challenge capital");
        }

        return instances.save(ChallengeInstance.start(UUID.randomUUID(), account.getAccountId(), definition,
                definition.startingCapital(), clock.instant()));
    }
}
