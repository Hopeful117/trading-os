package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.RulesRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerAccountServiceTest {
    @Test
    void refusesCrossUserAccessWithoutRevealingAccountData() {
        BrokerAccountRepository repository = mock(BrokerAccountRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        RulesRepository rulesRepository = mock(RulesRepository.class);
        com.hope.trading.trading_core.helper.AccountMapper accountMapper = mock(AccountMapper.class);

        UUID accountId = UUID.randomUUID();
        when(repository.existsById(accountId)).thenReturn(true);
        when(repository.findByIdAndOwnerId(accountId, UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn(Optional.empty());

        BrokerAccountService service = new BrokerAccountService(
                repository,
                accountRepository,
                rulesRepository,
                mock(UserRepository.class),
                mock(AccountMapper.class),
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(BrokerAccountOwnershipException.class,
                () -> service.get(UUID.fromString("00000000-0000-0000-0000-000000000002"), accountId));
    }
}
