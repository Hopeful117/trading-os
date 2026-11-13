package com.hope.trading.trading_core.tradeplanning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.risk.infrastructure.client.MarketValuationClient;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RealTradePlanT0IntegrationTest {
    private static final UUID PAPER_STANDARD_PROFILE =
            UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");

    @Autowired private OpportunityTradePlanOrchestrationService orchestration;
    @Autowired private AccountRepository accounts;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private UserRepository users;
    @Autowired private TradePlanningProfileService profiles;
    @Autowired private RiskPersistence riskPersistence;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private MarketIntelligenceTradePlanningClient marketIntelligence;
    @MockitoBean private TradePlanRiskPort tradePlans;
    @MockitoBean private RiskFactsProvider facts;
    @MockitoBean(name = "marketValuationClient") private MarketValuationClient market;
    @MockitoBean private RequiredMarginPort requiredMargins;

    private UUID actorId;
    private UUID accountId;
    private UUID brokerAccountId;
    private final UUID planId = UUID.randomUUID();
    private final Instant now = Instant.now().minusSeconds(30);

    @BeforeEach
    void setUp() {
        String fixtureSuffix = UUID.randomUUID().toString();
        User user = users.save(User.builder()
                .username("real-t0-" + fixtureSuffix)
                .password("test")
                .email("real-t0-" + fixtureSuffix + "@test.local")
                .role(Role.ROLE_USER)
                .build());
        actorId = user.getUserId();
        BrokerAccount broker = BrokerAccount.create(
                actorId, UUID.randomUUID(), ExecutionMode.LIVE, "test-live", now,
                PAPER_STANDARD_PROFILE, "1.0.0");
        brokerAccounts.save(broker);
        brokerAccountId = broker.id();
        Account account = Account.builder()
                .name("real-t0-account")
                .baseCurrency("USD")
                .broker(broker.providerRegistrationReference().toString())
                .brokerAccountId(broker.id())
                .peakEquity(new BigDecimal("10000"))
                .equity(new BigDecimal("10000"))
                .startingBalance(new BigDecimal("10000"))
                .user(user)
                .build();
        accountId = accounts.save(account).getAccountId();

        var profile = profiles.create(actorId, new TradePlanningProfileService.Values(
                new BigDecimal("100"), "USD", TradePlanningProfile.EntryType.MARKET,
                TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE, BigDecimal.ONE,
                TradePlanningProfile.TargetStrategy.RISK_MULTIPLE, BigDecimal.TWO,
                TradePlanningProfile.PlanningHorizon.INTRADAY,
                java.time.Duration.ofHours(1)));
        profiles.assign(actorId, accountId, profile.id(), profile.version());
        riskPersistence.configuration(accountId, broker.id(), "UTC", "USD", UUID.randomUUID());
        riskPersistence.assignProfile(accountId, PAPER_STANDARD_PROFILE, "1.0.0", now, "integration-fixture");

        when(marketIntelligence.generateFromOpportunity(any(), any(), any()))
                .thenReturn(new MarketIntelligenceTradePlanningClient.Response(planId, 1, false));
        when(tradePlans.loadPreflight(planId, 1)).thenReturn(plan());
        when(facts.load(any(), any(), any(), any(), any(), any()))
                .thenReturn(brokerFacts());
        when(market.value(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> valuation(
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(1)));
        when(requiredMargins.resolve(any())).thenReturn(Optional.of(
                new RequiredMarginPort.Fact(new BigDecimal("100"), "USD", "test-margin", 1, now)));
    }

    @Test
    void realOrchestrationRunsRealT0EngineAndPersistsEvaluation() {
        var response = orchestration.createFromOpportunity(
                actorId, UUID.randomUUID(), accountId, "real-t0-idempotency");

        assertThat(response.tradePlanId()).isEqualTo(planId);
        assertThat(response.tradePlanVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from risk_evaluation where trade_plan_id=? and trade_plan_version=? "
                        + "and account_id=? and evaluation_mode='PREFLIGHT'",
                Integer.class, planId, 1, accountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from risk_context_snapshot where evaluation_id in "
                        + "(select id from risk_evaluation where trade_plan_id=? and evaluation_mode='PREFLIGHT')",
                Integer.class, planId)).isPositive();
        assertThat(jdbc.queryForObject(
                "select status from risk_evaluation where trade_plan_id=? and evaluation_mode='PREFLIGHT'",
                String.class, planId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select decision from risk_evaluation where trade_plan_id=? and evaluation_mode='PREFLIGHT'",
                String.class, planId)).isIn("APPROVED", "APPROVED_WITH_WARNINGS", "REJECTED");
    }

    private TradePlanRiskPort.Snapshot plan() {
        return new TradePlanRiskPort.Snapshot(planId, 1, "PROPOSED", "OPPORTUNITY", now,
                UUID.randomUUID(), 1, now, actorId, accountId, "USD", UUID.randomUUID(), 1,
                UUID.randomUUID(), 1, "ETHUSD", null, null, "LONG",
                new EntryIntent(EntryIntent.OrderType.MARKET, null), new BigDecimal("90"),
                new BigDecimal("110"), BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("10"),
                BigDecimal.TEN, "USD", "{\"fixture\":true}", List.of());
    }

    private RiskFactsProvider.Snapshot brokerFacts() {
        return new RiskFactsProvider.Snapshot(brokerAccountId, 1, now, true, List.of(),
                Map.of("USD", new BigDecimal("10000")),
                new RiskFactsProvider.Account("USD", new BigDecimal("10000"),
                        new BigDecimal("10000"), new BigDecimal("100"), new BigDecimal("10000"),
                        RiskFactsProvider.EquityFact.available(new BigDecimal("10000"), "USD", now, now,
                                "test-broker", "reported")),
                List.of(), List.of(), List.of(), "{\"fixture\":true}");
    }

    private MarketValuationPort.Snapshot valuation(
            List<MarketValuationPort.Instrument> instruments,
            List<MarketValuationPort.Asset> assets, Instant capturedAt) {
        List<MarketValuationPort.Fact> values = new ArrayList<>();
        assets.forEach(asset -> values.add(new MarketValuationPort.Fact(
                "ASSET", asset.id(), null, asset.currency(), null,
                BigDecimal.ONE, null, null, "AVAILABLE", "test-market")));
        instruments.forEach(instrument -> values.add(new MarketValuationPort.Fact(
                "INSTRUMENT", instrument.id(), UUID.randomUUID(), null,
                instrument.priceUse(), new BigDecimal("100"), new BigDecimal("100"),
                BigDecimal.ONE, "AVAILABLE", "test-market")));
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", capturedAt, now,
                "test-market-v1", "PT1H", true, values, "{\"fixture\":true}");
    }
}
