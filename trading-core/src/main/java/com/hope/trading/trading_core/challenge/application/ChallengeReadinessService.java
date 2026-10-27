package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
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
import java.util.UUID;

@Service
public class ChallengeReadinessService {
    private final AccountRepository accounts;
    private final ChallengeInstanceRepository instances;
    private final ChallengeDefinitionRepository definitions;
    private final BrokerAccountRepository brokerAccounts;
    private final RiskPersistence risk;

    public ChallengeReadinessService(AccountRepository accounts, ChallengeInstanceRepository instances,
                                     ChallengeDefinitionRepository definitions, BrokerAccountRepository brokerAccounts,
                                     RiskPersistence risk) {
        this.accounts = accounts;
        this.instances = instances;
        this.definitions = definitions;
        this.brokerAccounts = brokerAccounts;
        this.risk = risk;
    }

    @Transactional(readOnly = true)
    public ChallengeReadiness resolve(UUID ownerId, UUID accountId) {
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null || account.getUser() == null || !ownerId.equals(account.getUser().getUserId())) {
            return ChallengeReadiness.blocked("ACCOUNT_UNAVAILABLE", "The account is not available to the caller", accountId);
        }

        ChallengeInstance challenge = instances.findForReadiness(accountId, ChallengeStatus.ACTIVE).orElse(null);
        if (challenge == null) {
            return ChallengeReadiness.ready(accountId, account, null, null);
        }

        ChallengeDefinition definition = definitions.findById(
                new ChallengeDefinitionKey(challenge.definitionId(), challenge.definitionVersion())).orElse(null);
        if (definition == null) {
            return ChallengeReadiness.blocked("CHALLENGE_NOT_ACTIVE", "The active Challenge definition is unavailable", accountId);
        }
        if (account.getStartingBalance() == null
                || account.getStartingBalance().compareTo(definition.startingCapital()) != 0
                || !definition.capitalCurrency().equalsIgnoreCase(account.getBaseCurrency())) {
            return ChallengeReadiness.blocked("ACCOUNT_BALANCE_MISMATCH",
                    "Account starting balance and currency do not match the Challenge definition", accountId,
                    challenge, definition, account);
        }
        var profile = risk.assignedProfile(accountId).orElse(null);
        if (profile == null || !definition.riskPolicyId().equals(profile.id())
                || !definition.riskPolicyVersion().equals(profile.semanticVersion())) {
            return ChallengeReadiness.blocked("RISK_POLICY_MISMATCH",
                    "The account Risk Policy does not match the active Challenge", accountId,
                    challenge, definition, account);
        }
        if (definition.riskTimeZone() != null) {
            var configuration = risk.configuration(accountId).orElse(null);
            if (configuration == null || !definition.riskTimeZone().equals(configuration.riskTimeZone())
                    || !definition.riskDayResetTime().toString().equals(configuration.riskDayResetTime())) {
                return ChallengeReadiness.blocked("RISK_POLICY_MISMATCH",
                        "The account Risk-day configuration does not match the Challenge", accountId,
                        challenge, definition, account);
            }
        }
        if (account.getBrokerAccountId() == null) {
            return ChallengeReadiness.blocked("ACCOUNT_UNAVAILABLE", "The Challenge broker account is unavailable", accountId,
                    challenge, definition, account);
        }
        BrokerAccount brokerAccount = brokerAccounts.findByIdAndOwnerId(account.getBrokerAccountId(), ownerId).orElse(null);
        if (brokerAccount == null) {
            return ChallengeReadiness.blocked("ACCOUNT_UNAVAILABLE", "The Challenge broker account is unavailable", accountId,
                    challenge, definition, account);
        }
        if (brokerAccount.connectionStatus() != BrokerConnectionStatus.CONNECTED
                || brokerAccount.credentialReference() == null) {
            return ChallengeReadiness.blocked("BROKER_NOT_READY",
                    "The Challenge broker account is not connected with valid credentials", accountId,
                    challenge, definition, account);
        }
        if (definition.requiredProviderRegistrationReference() != null
                && !definition.requiredProviderRegistrationReference().equals(
                brokerAccount.providerRegistrationReference())) {
            return ChallengeReadiness.blocked("PROVIDER_MISMATCH",
                    "The account broker provider is incompatible with the Challenge", accountId,
                    challenge, definition, account);
        }
        return ChallengeReadiness.ready(accountId, account, challenge, definition);
    }

    public record ChallengeReadiness(
            UUID accountId, boolean challengeActive, String status, String message,
            UUID challengeId, String challengeDefinitionVersion, String phase,
            BigDecimal challengeStartingCapital, String challengeCurrency,
            BigDecimal accountStartingBalance, BigDecimal accountEquity,
            UUID riskPolicyId, String riskPolicyVersion,
            UUID brokerAccountId, String requiredMarketProvider, UUID requiredProviderRegistrationReference) {
        static ChallengeReadiness ready(UUID accountId, Account account, ChallengeInstance challenge,
                                        ChallengeDefinition definition) {
            return new ChallengeReadiness(accountId, challenge != null, "READY", null,
                    challenge == null ? null : challenge.id(),
                    challenge == null ? null : challenge.definitionVersion(),
                    challenge == null ? null : challenge.currentPhaseKey(),
                    definition == null ? null : definition.startingCapital(),
                    definition == null ? null : definition.capitalCurrency(), account.getStartingBalance(),
                    account.getEquity(), definition == null ? null : definition.riskPolicyId(),
                    definition == null ? null : definition.riskPolicyVersion(), account.getBrokerAccountId(),
                    definition == null ? null : definition.requiredMarketProvider(),
                    definition == null ? null : definition.requiredProviderRegistrationReference());
        }

        static ChallengeReadiness blocked(String status, String message, UUID accountId) {
            return new ChallengeReadiness(accountId, false, status, message, null, null, null,
                    null, null, null, null, null, null, null, null, null);
        }

        static ChallengeReadiness blocked(String status, String message, UUID accountId,
                                          ChallengeInstance challenge, ChallengeDefinition definition, Account account) {
            return new ChallengeReadiness(accountId, true, status, message, challenge.id(),
                    challenge.definitionVersion(), challenge.currentPhaseKey(), definition.startingCapital(),
                    definition.capitalCurrency(), account.getStartingBalance(), account.getEquity(),
                    definition.riskPolicyId(), definition.riskPolicyVersion(), account.getBrokerAccountId(),
                    definition.requiredMarketProvider(), definition.requiredProviderRegistrationReference());
        }
    }
}
