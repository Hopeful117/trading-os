package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.challenge.application.ChallengeProgressService;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class TradeScenarioProjectionServiceTest {
    private final TradePlanRiskPort plans = mock(TradePlanRiskPort.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ChallengeProgressService challenges = mock(ChallengeProgressService.class);
    private final MarketValuationPort valuation = mock(MarketValuationPort.class);
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private final TradeScenarioProjectionService service = new TradeScenarioProjectionService(
            plans, accounts, challenges, valuation, clock);

    @Test
    void rejectsSnapshotThatDoesNotMatchRequestedIdentity() {
        UUID requestedId = UUID.randomUUID();
        UUID loadedId = UUID.randomUUID();
        when(plans.loadPreflight(requestedId, 4)).thenReturn(snapshot(loadedId, 4));

        RiskEvaluationException failure = assertThrows(RiskEvaluationException.class,
                () -> service.project(UUID.randomUUID(), requestedId, 4));

        assertEquals("TRADE_PLAN_VERSION_MISMATCH", failure.code());
        assertEquals(409, failure.status());
    }

    @Test
    void rejectsTradePlanOwnedByAnotherUserBeforeLoadingAccountFacts() {
        UUID planId = UUID.randomUUID();
        when(plans.loadPreflight(planId, 1)).thenReturn(snapshot(planId, 1));

        RiskEvaluationException failure = assertThrows(RiskEvaluationException.class,
                () -> service.project(UUID.randomUUID(), planId, 1));

        assertEquals("TRADE_PLAN_FORBIDDEN", failure.code());
        assertEquals(403, failure.status());
        verifyNoInteractions(accounts, challenges, valuation);
    }

    private TradePlanRiskPort.Snapshot snapshot(UUID id, long version) {
        return new TradePlanRiskPort.Snapshot(id, version, "ACCEPTED", "MANUAL", Instant.EPOCH,
                UUID.randomUUID(), 1, Instant.EPOCH, UUID.randomUUID(), UUID.randomUUID(), "USD",
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, "BTC/USD", "LONG", null,
                null, null, null, null, null, null, "USD", "{}", java.util.List.of());
    }
}
