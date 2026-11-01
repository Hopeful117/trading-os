package com.hope.trading.trading_core.risk.margin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.market_data.service.InstrumentFinancialContractResolver;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperRequiredMarginClientTest {
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void recalculatesFromTheSuppliedExecutionTimeValuationPrice() {
        UUID accountId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).brokerAccountId(brokerAccountId)
                .baseCurrency("USD")
                .user(User.builder().userId(UUID.randomUUID()).build()).build();
        BrokerAccount broker = mock(BrokerAccount.class);
        when(broker.id()).thenReturn(brokerAccountId);
        when(broker.ownerId()).thenReturn(account.getUser().getUserId());
        when(broker.executionMode()).thenReturn(ExecutionMode.PAPER);

        AccountRepository accounts = mock(AccountRepository.class);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        TradeRepository trades = mock(TradeRepository.class);
        MarginPolicyPersistence policies = mock(MarginPolicyPersistence.class);
        InstrumentFinancialContractResolver contracts = mock(InstrumentFinancialContractResolver.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(brokers.findById(brokerAccountId)).thenReturn(Optional.of(broker));
        when(trades.findAllByAccount_AccountId(accountId)).thenReturn(List.of());
        MarginPolicy policy = new MarginPolicy(policyId, "v1", "EUR/USD", new BigDecimal("0.01"),
                "USER_CONFIGURED", "test", NOW.minusSeconds(60), NOW.minusSeconds(60), 2,
                false, false, false, false, false);
        when(policies.version(policyId, "v1")).thenReturn(Optional.of(policy));
        when(contracts.resolve("EUR/USD")).thenReturn(contract());

        PaperRequiredMarginClient client = new PaperRequiredMarginClient(accounts, brokers, trades,
                policies, contracts, Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        RequiredMarginPort.Resolution t0 = client.resolveDetailed(request(accountId, brokerAccountId,
                policyId, valuation(new BigDecimal("1.10"), "PT5M"),
                MarketValuationPort.FreshnessRequirement.t0()));
        RequiredMarginPort.Resolution t1 = client.resolveDetailed(request(accountId, brokerAccountId,
                policyId, valuation(new BigDecimal("1.20"), "PT30S"),
                MarketValuationPort.FreshnessRequirement.t1()));

        assertThat(t0.fact().amount()).isEqualByComparingTo("11.00");
        assertThat(t1.fact().amount()).isEqualByComparingTo("12.00");
        assertThat(t1.fact().amount()).isNotEqualByComparingTo(t0.fact().amount());

        RequiredMarginPort.Resolution missingT1Price = client.resolveDetailed(request(accountId, brokerAccountId,
                policyId, valuation(null, "PT30S"), MarketValuationPort.FreshnessRequirement.t1()));
        assertThat(missingT1Price.fact()).isNull();
        assertThat(missingT1Price.reason()).isEqualTo("MISSING_MARK");

        RequiredMarginPort.Resolution staleT1 = client.resolveDetailed(request(accountId, brokerAccountId,
                policyId, valuation(new BigDecimal("1.20"), "PT30S", NOW.minusSeconds(31)),
                MarketValuationPort.FreshnessRequirement.t1()));
        assertThat(staleT1.fact()).isNull();
        assertThat(staleT1.reason()).isEqualTo("STALE_FACT");
    }

    @Test
    void rejectsAValuationWhoseFreshnessContractDoesNotMatchTheDecisionBoundary() {
        UUID accountId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).brokerAccountId(brokerAccountId)
                .baseCurrency("USD").user(User.builder().userId(UUID.randomUUID()).build()).build();
        BrokerAccount broker = mock(BrokerAccount.class);
        when(broker.id()).thenReturn(brokerAccountId);
        when(broker.ownerId()).thenReturn(account.getUser().getUserId());
        when(broker.executionMode()).thenReturn(ExecutionMode.PAPER);
        AccountRepository accounts = mock(AccountRepository.class);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        TradeRepository trades = mock(TradeRepository.class);
        MarginPolicyPersistence policies = mock(MarginPolicyPersistence.class);
        InstrumentFinancialContractResolver contracts = mock(InstrumentFinancialContractResolver.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(brokers.findById(brokerAccountId)).thenReturn(Optional.of(broker));
        when(trades.findAllByAccount_AccountId(accountId)).thenReturn(List.of());
        when(policies.version(policyId, "v1")).thenReturn(Optional.of(new MarginPolicy(policyId, "v1", "EUR/USD",
                new BigDecimal("0.01"), "USER_CONFIGURED", "test", NOW.minusSeconds(60), NOW.minusSeconds(60),
                2, false, false, false, false, false)));
        when(contracts.resolve("EUR/USD")).thenReturn(contract());
        PaperRequiredMarginClient client = new PaperRequiredMarginClient(accounts, brokers, trades,
                policies, contracts, Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        RequiredMarginPort.Resolution result = client.resolveDetailed(request(accountId, brokerAccountId,
                policyId, valuation(new BigDecimal("1.20"), "PT5M"),
                MarketValuationPort.FreshnessRequirement.t1()));

        assertThat(result.fact()).isNull();
        assertThat(result.reason()).isEqualTo("MARGIN_FRESHNESS_MISMATCH");
    }

    private RequiredMarginPort.Request request(UUID accountId, UUID brokerAccountId, UUID policyId,
                                               MarketValuationPort.Snapshot valuation,
                                               MarketValuationPort.FreshnessRequirement requirement) {
        UUID exposureId = UUID.randomUUID();
        return new RequiredMarginPort.Request(brokerAccountId, "EUR/USD", "LONG", new BigDecimal("1000"),
                new BigDecimal("1.10"), NOW, accountId, "USD", policyId.toString(), "v1", exposureId.toString(),
                new MarginResolutionContext(accountId, brokerAccountId, policyId, "v1", exposureId,
                        "market", 1L, valuation.snapshotId(), valuation.sourceVersion(), "USD", "USD",
                        BigDecimal.ONE, requirement.maxObservationAge(), 2, NOW), valuation, requirement);
    }

    private MarketValuationPort.Snapshot valuation(BigDecimal price, String maxAge) {
        return valuation(price, maxAge, NOW.minusSeconds(1));
    }

    private MarketValuationPort.Snapshot valuation(BigDecimal price, String maxAge, Instant observedAt) {
        MarketValuationPort.Fact fact = new MarketValuationPort.Fact("INSTRUMENT", "proposed", UUID.randomUUID(),
                null, MarketValuationPort.PriceUse.LAST, price, price, BigDecimal.ONE, "AVAILABLE", "paper-market",
                observedAt, observedAt, observedAt, observedAt, List.of());
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", observedAt, observedAt, "policy", maxAge,
                true, List.of(fact), "valuation");
    }

    private InstrumentFinancialContract contract() {
        return new InstrumentFinancialContract("EUR/USD", InstrumentProductType.CFD,
                new CurrencyCode("EUR"), new CurrencyCode("USD"), QuantityUnit.UNDERLYING_UNITS,
                BigDecimal.ONE, 5, 0, BigDecimal.ONE, BigDecimal.ONE,
                new FinancialContractProvenance("market", 1L, NOW.minusSeconds(1)));
    }
}
