package com.hope.trading.broker_service.ctrader.e2e;

import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provider boundary double for the isolated E2E runtime.
 * No network client or external broker endpoint is used by this component.
 */
@Component
@Primary
@Profile("e2e-controlled-broker")
public final class ControlledCTraderOpenApiClient implements CTraderOpenApiClient {
    private final AtomicInteger submissions = new AtomicInteger();
    private final Map<String, String> orders = new ConcurrentHashMap<>();
    private final String outcome;

    public ControlledCTraderOpenApiClient(
            @Value("${e2e.controlled-broker.outcome:ACCEPT}") String outcome) {
        this.outcome = outcome.strip().toUpperCase();
    }

    public int submissionCount() {
        return submissions.get();
    }

    @Override
    public List<String> discoverAuthorizedAccountIds(CTraderToken token) {
        return List.of("e2e-ctrader-account");
    }

    @Override
    public void authorizeAccount(CTraderToken token, String externalAccountId) {
        requireAccount(externalAccountId);
    }

    @Override
    public boolean isDemoAccount(CTraderToken token, String externalAccountId) {
        requireAccount(externalAccountId);
        return true;
    }

    @Override
    public CTraderOrderResponse submitOrder(CTraderToken token, CTraderOrderRequest request) {
        if (!"ACCEPT".equals(outcome)) {
            if ("TIMEOUT".equals(outcome)) {
                throw new CTraderAuthenticationException("controlled broker timeout");
            }
            if ("UNKNOWN".equals(outcome)) {
                throw new IllegalStateException("controlled broker unknown outcome");
            }
            throw new CTraderAuthenticationException("controlled broker rejected order");
        }
        int count = submissions.incrementAndGet();
        String externalOrderId = "e2e-order-" + count;
        orders.put(request.clientOrderId(), externalOrderId);
        return new CTraderOrderResponse(externalOrderId);
    }

    @Override
    public CTraderExpectedMargin expectedMargin(CTraderToken token, String externalAccountId,
                                                 String instrument, String side, BigDecimal quantity) {
        requireAccount(externalAccountId);
        if (instrument == null || instrument.isBlank() || side == null || side.isBlank()
                || quantity == null || quantity.signum() <= 0) {
            throw new CTraderAuthenticationException("controlled broker margin request rejected");
        }
        return new CTraderExpectedMargin(instrument, side, quantity.multiply(new BigDecimal("1000")),
                "USD", 1L, Instant.now());
    }

    @Override
    public CTraderReadOnlyObservation readOnlyObservation(CTraderToken token, String externalAccountId,
                                                           Instant from, Instant to) {
        requireAccount(externalAccountId);
        Instant now = Instant.now();
        List<CTraderReadOnlyObservation.Order> snapshots = orders.entrySet().stream()
                .map(entry -> new CTraderReadOnlyObservation.Order(entry.getValue(), entry.getKey(),
                        "ACCEPTED", BigDecimal.ONE, BigDecimal.ONE, now))
                .toList();
        var accountFacts = new CTraderReadOnlyObservation.AccountFacts(
                CTraderReadOnlyObservation.ProviderField.present("TRADING"),
                CTraderReadOnlyObservation.ProviderField.present("HEDGED"),
                CTraderReadOnlyObservation.ProviderField.present("USD"),
                CTraderReadOnlyObservation.ProviderField.present(2),
                CTraderReadOnlyObservation.ProviderField.present(100000L),
                CTraderReadOnlyObservation.ProviderField.present(BigDecimal.ZERO),
                CTraderReadOnlyObservation.ProviderField.present(BigDecimal.ZERO),
                CTraderReadOnlyObservation.ProviderField.present(BigDecimal.ZERO),
                CTraderReadOnlyObservation.ProviderField.present(1L));
        return new CTraderReadOnlyObservation(externalAccountId, "USD", new BigDecimal("100000"),
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), List.of(), snapshots,
                List.of(), now.minusSeconds(1), now.minusSeconds(1), now.minusSeconds(1), true, List.of(),
                accountFacts, CTraderReadOnlyObservation.CollectionState.available(List.of()),
                CTraderReadOnlyObservation.CollectionState.available(snapshots),
                CTraderReadOnlyObservation.CollectionState.available(List.of()), true,
                CTraderReadOnlyObservation.CollectionState.available(List.of()));
    }

    private void requireAccount(String externalAccountId) {
        if (!"e2e-ctrader-account".equals(externalAccountId)) {
            throw new CTraderAuthenticationException("controlled broker account rejected");
        }
    }
}
