package com.hope.trading.trading_core.risk.infrastructure.client;

import com.hope.trading.trading_core.config.BrokerServiceFeignConfiguration;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Value;

@FeignClient(name = "broker-service", contextId = "brokerRequiredMarginClient",
        configuration = BrokerServiceFeignConfiguration.class)
interface BrokerMarginFeignClient {
    @PostMapping("/internal/v1/broker-accounts/{id}/margin-preview")
    BrokerMarginPreview preview(@PathVariable UUID id, @RequestBody BrokerMarginPreviewRequest request);
}

record BrokerMarginPreviewRequest(UUID brokerAccountId, String instrument, String side,
                                 BigDecimal quantity, BigDecimal price, BigDecimal leverage) { }

record BrokerMarginPreview(UUID brokerAccountId, String instrument, BigDecimal amount,
                           String currency, String sourceId, long sourceVersion, Instant observedAt) { }

@org.springframework.stereotype.Component
public final class BrokerRequiredMarginClient implements RequiredMarginPort {
    private final BrokerMarginFeignClient client;
    private final BrokerTechnicalCapabilitiesFeignClient capabilities;
    private final BrokerAccountRepository brokerAccounts;
    private final AccountRepository accounts;
    private final Clock clock;
    private final Duration maxAge;

    public BrokerRequiredMarginClient(BrokerMarginFeignClient client,
                                      BrokerTechnicalCapabilitiesFeignClient capabilities,
                                      BrokerAccountRepository brokerAccounts, AccountRepository accounts,
                                      Clock clock, @Value("${trading-core.risk.broker-facts-max-age:PT5M}") Duration maxAge) {
        this.client = client; this.capabilities = capabilities;
        this.brokerAccounts = brokerAccounts; this.accounts = accounts;
        this.clock = clock; this.maxAge = maxAge;
    }

    @Override
    public Optional<Fact> resolve(Request request) {
        if (request == null || request.brokerAccountId() == null || blank(request.instrument())
                 || (!"LONG".equalsIgnoreCase(request.direction()) && !"SHORT".equalsIgnoreCase(request.direction()))
                 || request.quantity() == null || request.quantity().signum() <= 0
                || request.price() == null || request.price().signum() <= 0) return Optional.empty();
        try {
            var brokerAccount = brokerAccounts.findById(request.brokerAccountId()).orElse(null);
            if (brokerAccount == null) return Optional.empty();
            if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
                return Optional.empty();
            }
            BrokerTechnicalCapabilities technical = capabilities.get(request.brokerAccountId(), request.instrument());
            if (technical == null || !request.brokerAccountId().equals(technical.brokerAccountId())
                    || blank(technical.provider()) || technical.sourceVersion() < 1 || technical.observedAt() == null
                    || !request.instrument().equalsIgnoreCase(technical.instrument())
                    || technical.supportedOrderTypes() == null || technical.supportedOrderTypes().isEmpty()
                 || invalidLeverageLevels(technical.supportedBuyLeverageLevels())
                     || invalidLeverageLevels(technical.supportedSellLeverageLevels())
                     || stale(technical.observedAt())) return Optional.empty();
            BrokerMarginPreview preview = client.preview(request.brokerAccountId(),
                    new BrokerMarginPreviewRequest(request.brokerAccountId(), request.instrument(),
                            "LONG".equalsIgnoreCase(request.direction()) ? "BUY" : "SELL",
                            request.quantity(), request.price(), null));
            if (preview == null || !request.brokerAccountId().equals(preview.brokerAccountId())
                    || !request.instrument().equalsIgnoreCase(preview.instrument())
                    || preview.amount() == null || preview.amount().signum() <= 0
                    || blank(preview.currency()) || blank(preview.sourceId())
                    || preview.observedAt() == null || preview.sourceVersion() < 1
                    || stale(preview.observedAt())) return Optional.empty();
            return Optional.of(new Fact(preview.amount(), preview.currency(), preview.sourceId(),
                    preview.sourceVersion(), preview.observedAt()));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private boolean stale(Instant observedAt) {
        Instant now = clock.instant();
        return observedAt.isAfter(now) || observedAt.plus(maxAge).isBefore(now);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean invalidLeverageLevels(java.util.List<BigDecimal> levels) {
        return levels == null || levels.isEmpty() || levels.stream()
                .anyMatch(level -> level == null || level.signum() <= 0);
    }
}
