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
        if (request.price() == null) return Optional.empty();
        try {
            var brokerAccount = brokerAccounts.findById(request.brokerAccountId()).orElse(null);
            if (brokerAccount == null) return Optional.empty();
            if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
                var account = accounts.findByBrokerAccountId(request.brokerAccountId()).orElse(null);
                if (account == null || account.getBaseCurrency() == null) return Optional.empty();
                return Optional.of(new Fact(request.quantity().multiply(request.price()),
                        account.getBaseCurrency(), "TRADING_CORE:PAPER_MARGIN", Math.max(1, account.getVersion()),
                        request.requestedAt()));
            }
            BrokerTechnicalCapabilities technical = capabilities.get(request.brokerAccountId(), request.instrument());
            if (technical == null || technical.sourceVersion() < 1 || technical.observedAt() == null
                    || !request.instrument().equalsIgnoreCase(technical.instrument())
                    || stale(technical.observedAt())) return Optional.empty();
            BrokerMarginPreview preview = client.preview(request.brokerAccountId(),
                    new BrokerMarginPreviewRequest(request.brokerAccountId(), request.instrument(),
                            "LONG".equalsIgnoreCase(request.direction()) ? "BUY" : "SELL",
                            request.quantity(), request.price(), null));
            if (preview == null || preview.amount() == null || preview.amount().signum() <= 0
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
}
