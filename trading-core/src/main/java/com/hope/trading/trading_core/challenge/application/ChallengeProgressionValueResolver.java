package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Resolves only progression values that are authoritative for the definition source. */
final class ChallengeProgressionValueResolver {
    Optional<BigDecimal> resolve(Account account, ChallengeDefinition definition) {
        return resolve(account, definition, definition.progressionValueSource());
    }

    Optional<BigDecimal> resolve(Account account, ChallengeDefinition definition,
                                 ProgressionValueSource source) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(source, "source");
        if (source == ProgressionValueSource.EQUITY) {
            return authoritativeEquity(account);
        }
        return balance(account, definition);
    }

    private Optional<BigDecimal> authoritativeEquity(Account account) {
        if (account.getSettlementModel() != SettlementModel.CASH
                || account.getValuationStatus() != AccountValuationStatus.FRESH
                || account.getEquity() == null || account.getValuedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(account.getEquity());
    }

    private Optional<BigDecimal> balance(Account account, ChallengeDefinition definition) {
        if (account.getSettlementModel() == SettlementModel.CASH && account.getRealizedBalance() != null) {
            return Optional.of(account.getRealizedBalance());
        }
        if (account.getBalances() == null) return Optional.empty();
        return account.getBalances().stream()
                .filter(Objects::nonNull)
                .filter(value -> definition.capitalCurrency().equalsIgnoreCase(value.getAsset()))
                .map(value -> value.getAmount())
                .filter(Objects::nonNull)
                .findFirst();
    }
}
