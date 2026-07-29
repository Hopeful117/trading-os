package com.hope.trading.trading_core.brokeraccount.application;

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
        UUID accountId = UUID.randomUUID();
        when(repository.existsById(accountId)).thenReturn(true);
        when(repository.findByIdAndOwnerId(accountId, UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn(Optional.empty());
        BrokerAccountService service = new BrokerAccountService(repository,
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC));

        assertThrows(BrokerAccountOwnershipException.class,
                () -> service.get(UUID.fromString("00000000-0000-0000-0000-000000000002"), accountId));
    }
}
