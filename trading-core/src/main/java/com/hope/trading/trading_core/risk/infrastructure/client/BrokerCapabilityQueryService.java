package com.hope.trading.trading_core.risk.infrastructure.client;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.service.AccountService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class BrokerCapabilityQueryService {
    private final AccountService accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final BrokerTechnicalCapabilitiesFeignClient capabilities;
    private final Clock clock;
    private final Duration maxAge;

    public BrokerCapabilityQueryService(AccountService accounts,
                                        BrokerAccountRepository brokerAccounts,
                                        BrokerTechnicalCapabilitiesFeignClient capabilities,
                                        Clock clock,
                                        @Value("${trading-core.risk.broker-facts-max-age:PT5M}") Duration maxAge) {
        this.accounts = accounts;
        this.brokerAccounts = brokerAccounts;
        this.capabilities = capabilities;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    public List<MarketCapability> resolve(UUID accountId, String username, List<String> instruments) {
        var account = accounts.getAccountDtoById(accountId, username);
        if (account.getBrokerAccountId() == null || account.getUserId() == null) {
            return normalize(instruments).stream().map(this::unavailable).toList();
        }
        var brokerAccount = brokerAccounts.findByIdAndOwnerId(account.getBrokerAccountId(), account.getUserId())
                .orElse(null);
        if (brokerAccount == null || brokerAccount.provider() == null) {
            return normalize(instruments).stream().map(this::unavailable).toList();
        }
        return normalize(instruments).stream()
                .map(instrument -> resolveOne(account.getBrokerAccountId(), brokerAccount.executionMode(),
                        brokerAccount.provider().name(), instrument))
                .toList();
    }

    private MarketCapability resolveOne(UUID brokerAccountId, ExecutionMode mode, String provider,
                                        String instrument) {
        try {
            BrokerTechnicalCapabilities value = mode == ExecutionMode.PAPER
                    ? capabilities.getByProvider(provider, brokerAccountId, instrument)
                    : capabilities.get(brokerAccountId, instrument);
            if (value == null || value.sourceVersion() < 1 || value.observedAt() == null
                    || value.observedAt().isAfter(clock.instant())
                    || value.observedAt().plus(maxAge).isBefore(clock.instant())
                    || value.instrument() == null || !instrument.equalsIgnoreCase(value.instrument())
                    || value.supportedOrderTypes() == null || value.supportedOrderTypes().isEmpty()) {
                return unavailable(instrument);
            }
            List<String> orderTypes = value.supportedOrderTypes().stream()
                    .filter(type -> type != null && !type.isBlank())
                    .map(type -> type.toUpperCase(Locale.ROOT))
                    .distinct().toList();
            if (!orderTypes.contains("MARKET")) {
                return new MarketCapability(instrument, false, orderTypes,
                        value.supportedBuyLeverageLevels(), value.supportedSellLeverageLevels(),
                        value.sourceVersion(), value.observedAt(), provider,
                        List.of("BROKER_MARKET_ORDER_UNSUPPORTED"));
            }
            return new MarketCapability(instrument, true, orderTypes,
                    value.supportedBuyLeverageLevels(), value.supportedSellLeverageLevels(),
                    value.sourceVersion(), value.observedAt(), provider, List.of());
        } catch (RuntimeException unavailable) {
            return unavailable(instrument);
        }
    }

    private MarketCapability unavailable(String instrument) {
        return new MarketCapability(instrument, false, List.of(), List.of(), List.of(),
                0, null, null, List.of("BROKER_CAPABILITY_UNAVAILABLE"));
    }

    private List<String> normalize(List<String> instruments) {
        return instruments == null ? List.of() : instruments.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct().toList();
    }

    public record MarketCapability(String instrument, boolean available, List<String> supportedOrderTypes,
                                   List<BigDecimal> supportedBuyLeverageLevels,
                                   List<BigDecimal> supportedSellLeverageLevels, long sourceVersion,
                                   Instant observedAt, String provider, List<String> reasons) {
        public MarketCapability {
            supportedOrderTypes = List.copyOf(supportedOrderTypes);
            supportedBuyLeverageLevels = List.copyOf(supportedBuyLeverageLevels);
            supportedSellLeverageLevels = List.copyOf(supportedSellLeverageLevels);
            reasons = List.copyOf(reasons);
        }
    }
}
