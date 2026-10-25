package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.challenge.application.ChallengeNotStartedException;
import com.hope.trading.trading_core.challenge.application.ChallengeProgressService;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.model.PositionIntelligenceView;
import com.hope.trading.trading_core.dashboard.model.PositionProtectionStatus;
import com.hope.trading.trading_core.dashboard.model.PositionSource;
import com.hope.trading.trading_core.dashboard.model.PositionValuationStatus;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PositionIntelligenceQueryServiceTest {
    private final TradeOutcomeRepositoryPort outcomes = mock(TradeOutcomeRepositoryPort.class);
    private final MarketIntelligenceTradePlanningClient tradePlans = mock(MarketIntelligenceTradePlanningClient.class);
    private final ChallengeProgressService challenges = mock(ChallengeProgressService.class);
    private final Instant now = Instant.parse("2026-10-25T00:00:00Z");
    private final PositionIntelligenceQueryService service = new PositionIntelligenceQueryService(
            outcomes, tradePlans, challenges, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void composesComparableFactsAndCurrentPositionConsequencesWithoutLineageInference() {
        UUID accountId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).baseCurrency("USD")
                .user(user(actorId)).build();
        OpenPositionDashboardView position = new OpenPositionDashboardView(
                "broker-position", accountId, UUID.randomUUID(), "BTC/USD", TradeType.BUY,
                new BigDecimal("2"), new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("90"), new BigDecimal("130"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("40"),
                new BigDecimal("4"), new BigDecimal("220"), PositionProtectionStatus.PROTECTED,
                true, now.minusSeconds(300), now.minusSeconds(10), now,
                PositionSource.BROKER, PositionValuationStatus.FRESH, "USD");
        when(challenges.get(accountId, actorId)).thenThrow(new ChallengeNotStartedException("none"));

        PositionIntelligenceView result = service.query(actorId, account, position,
                new BigDecimal("1000"), new BigDecimal("1000"));

        assertThat(result.status()).isEqualTo("AVAILABLE");
        assertThat(result.lineage().status()).isEqualTo("UNAVAILABLE");
        assertThat(result.lineage().reason()).isEqualTo("LIVE_POSITION_LINEAGE_NOT_PERSISTED");
        assertThat(result.comparisons().priceChange()).isEqualByComparingTo("10");
        assertThat(result.comparisons().priceChangePercentage()).isEqualByComparingTo("10");
        assertThat(result.comparisons().distanceToStop()).isEqualByComparingTo("20");
        assertThat(result.comparisons().distanceToTarget()).isEqualByComparingTo("20");
        assertThat(result.scenarios().economics().monetaryRisk()).isEqualByComparingTo("40");
        assertThat(result.scenarios().economics().monetaryReward()).isEqualByComparingTo("40");
        assertThat(result.scenarios().economics().riskReward()).isEqualByComparingTo("1");
        assertThat(result.accountData().status()).isEqualTo("AVAILABLE");
        assertThat(result.marketData().status()).isEqualTo("AVAILABLE");
        assertThat(result.challengeData().status()).isEqualTo("NOT_APPLICABLE");
        verifyNoInteractions(outcomes, tradePlans);
    }

    private com.hope.trading.trading_core.model.User user(UUID id) {
        var user = new com.hope.trading.trading_core.model.User();
        user.setUserId(id);
        return user;
    }
}
