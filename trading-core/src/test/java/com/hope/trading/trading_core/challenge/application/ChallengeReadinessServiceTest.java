package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChallengeReadinessServiceTest {
    @Test
    void blocksChallengeWhenBrokerIsNotConnectedWithCredentials() {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID riskPolicyId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        AccountRepository accounts = mock(AccountRepository.class);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
        RiskPersistence risk = mock(RiskPersistence.class);
        ChallengeReadinessService service = new ChallengeReadinessService(
                accounts, instances, definitions, brokerAccounts, risk);

        User user = mock(User.class);
        when(user.getUserId()).thenReturn(ownerId);
        Account account = mock(Account.class);
        when(account.getUser()).thenReturn(user);
        when(account.getStartingBalance()).thenReturn(new BigDecimal("50000"));
        when(account.getBaseCurrency()).thenReturn("USD");
        when(account.getBrokerAccountId()).thenReturn(brokerAccountId);
        when(account.getEquity()).thenReturn(new BigDecimal("50000"));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        ChallengeInstance challenge = mock(ChallengeInstance.class);
        when(challenge.definitionId()).thenReturn(definitionId);
        when(challenge.definitionVersion()).thenReturn("1.0.0");
        when(instances.findForReadiness(accountId, ChallengeStatus.ACTIVE))
                .thenReturn(Optional.of(challenge));

        UUID registration = UUID.randomUUID();
        ChallengeDefinition definition = mock(ChallengeDefinition.class);
        when(definition.startingCapital()).thenReturn(new BigDecimal("50000"));
        when(definition.capitalCurrency()).thenReturn("USD");
        when(definition.riskPolicyId()).thenReturn(riskPolicyId);
        when(definition.riskPolicyVersion()).thenReturn("1.0.0");
        when(definition.requiredProviderRegistrationReference()).thenReturn(registration);
        when(definitions.findById(new ChallengeDefinitionKey(definitionId, "1.0.0")))
                .thenReturn(Optional.of(definition));
        when(risk.assignedProfile(accountId)).thenReturn(Optional.of(new RiskPersistence.Profile(
                riskPolicyId, "1.0.0", "POLICY", "1.0.0", "PROP_FIRM", null,
                "test", null, null, java.util.List.of())));

        BrokerAccount brokerAccount = mock(BrokerAccount.class);
        when(brokerAccount.connectionStatus()).thenReturn(BrokerConnectionStatus.DISCONNECTED);
        when(brokerAccount.providerRegistrationReference()).thenReturn(registration);
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, ownerId)).thenReturn(Optional.of(brokerAccount));

        ChallengeReadinessService.ChallengeReadiness result = service.resolve(ownerId, accountId);

        assertThat(result.status()).isEqualTo("BROKER_NOT_READY");
        assertThat(result.challengeActive()).isTrue();
        verify(brokerAccounts).findByIdAndOwnerId(brokerAccountId, ownerId);
    }
}
