package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerDealSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerSynchronizationPersistenceIntegrationTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired private BrokerSynchronizationService service;
    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private TradeRepository trades;
    @Autowired private EntityManager entityManager;

    @MockitoBean private BrokerAccountSnapshotV2Client snapshotClient;

    private UUID ownerId;
    private UUID brokerAccountId;
    private UUID tradeId;

    @BeforeEach
    void setUp() {
        User owner = users.saveAndFlush(User.builder()
                .username("reconciliation-persistence-" + UUID.randomUUID())
                .password("password")
                .email(UUID.randomUUID() + "@test.local")
                .role(Role.ROLE_USER)
                .build());
        ownerId = owner.getUserId();

        BrokerAccount brokerAccount = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN,
                ExecutionMode.LIVE, "cTrader", OBSERVED_AT);
        brokerAccountId = brokerAccounts.saveAndFlush(brokerAccount).id();

        Account account = accounts.saveAndFlush(Account.builder()
                .brokerAccountId(brokerAccountId)
                .broker(BrokerProvider.KRAKEN.toString())
                .name("cTrader")
                .baseCurrency("USD")
                .equity(new BigDecimal("10000"))
                .peakEquity(new BigDecimal("10000"))
                .user(owner)
                .build());

        tradeId = trades.saveAndFlush(Trade.builder()
                .account(account)
                .symbol("XBT/USD")
                .type(TradeType.BUY)
                .quantity(new BigDecimal("2"))
                .entryPrice(new BigDecimal("100"))
                .openedAt(OBSERVED_AT)
                .tradeStatus(TradeStatus.OPEN)
                .brokerPositionReference("position-1")
                .brokerOriginalQuantity(new BigDecimal("2"))
                .build()).getTradeId();
    }

    @Test
    void persistsCloseFactsAndDoesNotResurrectAfterReload() {
        BrokerAccountSnapshotV2Dto closedSnapshot = snapshot(List.of(),
                List.of(closeDeal("deal-1", "position-1", "2")));
        when(snapshotClient.getAccountSnapshot(brokerAccountId)).thenReturn(closedSnapshot);

        entityManager.flush();
        entityManager.clear();
        service.synchronize(ownerId, brokerAccountId);
        entityManager.flush();
        entityManager.clear();

        Trade reloadedClosedTrade = trades.findById(tradeId).orElseThrow();
        assertThat(reloadedClosedTrade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(reloadedClosedTrade.getBrokerPositionReference()).isEqualTo("position-1");
        assertThat(reloadedClosedTrade.getBrokerCloseDealReferences()).isEqualTo("deal-1");
        assertThat(reloadedClosedTrade.getRealizedPnl()).isEqualByComparingTo("5");

        when(snapshotClient.getAccountSnapshot(brokerAccountId)).thenReturn(
                snapshot(List.of(position("position-1", "2")),
                        List.of(closeDeal("deal-1", "position-1", "2"))));
        service.synchronize(ownerId, brokerAccountId);
        entityManager.flush();
        entityManager.clear();

        Trade afterResurrectionAttempt = trades.findById(tradeId).orElseThrow();
        assertThat(afterResurrectionAttempt.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(afterResurrectionAttempt.getRealizedPnl()).isEqualByComparingTo("5");
    }

    @Test
    void persistsHistoricalCloseBaselineAndAppliesItOnceAfterPositionDisappears() {
        BrokerAccountSnapshotV2Dto importedSnapshot = snapshot(
                List.of(position("position-2", "2")),
                List.of(closeDeal("historical-deal", "position-2", "1")));
        when(snapshotClient.getAccountSnapshot(brokerAccountId)).thenReturn(importedSnapshot);

        service.synchronize(ownerId, brokerAccountId);
        entityManager.flush();
        entityManager.clear();

        Trade reloadedImportedTrade = trades.findAll().stream()
                .filter(trade -> "position-2".equals(trade.getBrokerPositionReference()))
                .findFirst()
                .orElseThrow();
        assertThat(reloadedImportedTrade.getBrokerBaselineCloseDealReferences())
                .isEqualTo("historical-deal");
        assertThat(reloadedImportedTrade.getBrokerCloseDealReferences()).isNull();
        assertThat(reloadedImportedTrade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);

        when(snapshotClient.getAccountSnapshot(brokerAccountId)).thenReturn(
                snapshot(List.of(), List.of(closeDeal("historical-deal", "position-2", "1"))));
        service.synchronize(ownerId, brokerAccountId);
        entityManager.flush();
        entityManager.clear();

        Trade reloadedClosedTrade = trades.findById(reloadedImportedTrade.getTradeId()).orElseThrow();
        assertThat(reloadedClosedTrade.getBrokerCloseDealReferences()).isEqualTo("historical-deal");
        assertThat(reloadedClosedTrade.getBrokerClosedQuantity()).isEqualByComparingTo("1");
        assertThat(reloadedClosedTrade.getRealizedPnl()).isEqualByComparingTo("5");
        assertThat(reloadedClosedTrade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);

        service.synchronize(ownerId, brokerAccountId);
        entityManager.flush();
        entityManager.clear();

        Trade afterRepeatedClose = trades.findById(reloadedImportedTrade.getTradeId()).orElseThrow();
        assertThat(afterRepeatedClose.getBrokerClosedQuantity()).isEqualByComparingTo("1");
        assertThat(afterRepeatedClose.getRealizedPnl()).isEqualByComparingTo("5");
    }

    private BrokerAccountSnapshotV2Dto snapshot(List<BrokerPositionSnapshotV2Dto> positions,
                                                List<BrokerDealSnapshotV2Dto> deals) {
        BrokerAccount account = brokerAccounts.findById(brokerAccountId).orElseThrow();
        return new BrokerAccountSnapshotV2Dto(brokerAccountId, account.providerRegistrationReference(),
                null, Map.of("USD", new BigDecimal("10000")), positions, OBSERVED_AT,
                null, List.of(), deals, "COMPLETE", "FRESH", OBSERVED_AT, OBSERVED_AT, List.of());
    }

    private BrokerPositionSnapshotV2Dto position(String reference, String quantity) {
        return new BrokerPositionSnapshotV2Dto("XBT/USD", new BigDecimal(quantity),
                new BigDecimal("100"), OBSERVED_AT, reference);
    }

    private BrokerDealSnapshotV2Dto closeDeal(String dealReference, String positionReference, String quantity) {
        return new BrokerDealSnapshotV2Dto(dealReference, positionReference, "XBT/USD", "USD", "SELL",
                new BigDecimal(quantity), new BigDecimal("110"), null, null, new BigDecimal("5"),
                OBSERVED_AT, true);
    }
}
