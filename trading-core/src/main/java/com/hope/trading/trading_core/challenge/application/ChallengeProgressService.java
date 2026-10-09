package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ChallengeProgressService {
    private final AccountRepository accounts;
    private final ChallengeInstanceRepository instances;
    private final ChallengeDefinitionRepository definitions;

    public ChallengeProgressService(AccountRepository accounts, ChallengeInstanceRepository instances,
                                    ChallengeDefinitionRepository definitions) {
        this.accounts = accounts;
        this.instances = instances;
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public ChallengeProgress get(UUID accountId, UUID ownerId) {
        Account account = accounts.findById(accountId).orElseThrow();
        if (account.getUser() == null || !ownerId.equals(account.getUser().getUserId())) {
            throw new IllegalArgumentException("Account is not owned by the caller");
        }
        ChallengeInstance challenge = instances.findTopByAccountIdOrderByStartedAtDesc(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account has no Challenge"));
        ChallengeDefinition definition = definitions.findById(
                        new ChallengeDefinitionKey(challenge.definitionId(), challenge.definitionVersion()))
                .orElseThrow(() -> new IllegalStateException("Challenge definition does not exist"));
        BigDecimal current = account.getBalances().stream()
                .filter(value -> definition.capitalCurrency().equalsIgnoreCase(value.getAsset()))
                .map(value -> value.getAmount()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Challenge balance is unavailable"));
        BigDecimal target = definition.startingCapital()
                .add(definition.startingCapital().multiply(definition.profitTargetRatio()));
        BigDecimal profit = current.subtract(challenge.startingCapital());
        BigDecimal progress = target.compareTo(challenge.startingCapital()) == 0
                ? BigDecimal.ONE : profit.divide(target.subtract(challenge.startingCapital()), 12,
                java.math.RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return new ChallengeProgress(challenge.id(), accountId, challenge.status(), challenge.startingCapital(),
                current, target, profit, progress, null, null, null,
                challenge.status() == com.hope.trading.trading_core.challenge.domain.ChallengeStatus.PASSED
                        ? challenge.passedAt() : challenge.breachedAt());
    }
}
