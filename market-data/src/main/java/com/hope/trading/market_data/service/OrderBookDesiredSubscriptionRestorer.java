package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.OrderBookAcquisitionProperties;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.websocket.KrakenStreamLifecycleDiagnostics;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.repository.MarketRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderBookDesiredSubscriptionRestorer {
    private final OrderBookAcquisitionProperties properties;
    private final MarketRepository marketRepository;
    private final MarketSubscriptionService subscriptionService;
    private final KrakenStreamLifecycleDiagnostics diagnostics;
    private final Map<SubscriptionKey, RestoredSubscription> restoredSubscriptions = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void restoreOnApplicationReady() {
        restoreConfiguredSubscriptions();
    }

    synchronized void restoreConfiguredSubscriptions() {
        if (!properties.isStartupEnabled()) {
            return;
        }
        List<OrderBookAcquisitionProperties.DesiredSubscription> desiredSubscriptions =
                properties.getDesiredSubscriptions();
        if (desiredSubscriptions == null || desiredSubscriptions.isEmpty()) {
            return;
        }

        for (OrderBookAcquisitionProperties.DesiredSubscription desired : desiredSubscriptions) {
            SubscriptionKey key = SubscriptionKey.from(desired);
            if (restoredSubscriptions.containsKey(key)) {
                continue;
            }
            restore(desired, key);
        }
    }

    private void restore(OrderBookAcquisitionProperties.DesiredSubscription desired, SubscriptionKey key) {
        diagnostics.desiredSubscriptionRestoreStarted(
                desired.getProvider(), desired.getProductType(), desired.getSymbol(),
                desired.getProviderSymbol(), desired.getDepth());
        Market market;
        MarketStreamRequest request;
        try {
            market = resolveMarket(desired);
            int depth = validateMarket(desired, market);
            request = new MarketStreamRequest(
                    MarketStreamType.ORDER_BOOK,
                    new MarketStreamParameters(null, depth));
        } catch (RuntimeException exception) {
            diagnostics.desiredSubscriptionRestoreFailed(
                    desired.getProvider(), desired.getProductType(), desired.getSymbol(),
                    desired.getProviderSymbol(), desired.getDepth(), "VALIDATION", exception);
            return;
        }
        try {
            subscriptionService.subscribe(market.getMarketId(), request);
            restoredSubscriptions.put(key, new RestoredSubscription(market.getMarketId(), request));
            diagnostics.desiredSubscriptionRestored(
                    desired.getProvider(), desired.getProductType(), desired.getSymbol(),
                    desired.getProviderSymbol(), desired.getDepth());
        } catch (RuntimeException exception) {
            diagnostics.desiredSubscriptionRestoreFailed(
                    desired.getProvider(), desired.getProductType(), desired.getSymbol(),
                    desired.getProviderSymbol(), desired.getDepth(), "SUBSCRIPTION", exception);
        }
    }

    private Market resolveMarket(OrderBookAcquisitionProperties.DesiredSubscription desired) {
        MarketProvider provider = parseProvider(desired.getProvider());
        InstrumentProductType productType = parseProductType(desired.getProductType());
        if (desired.getSymbol() == null || desired.getSymbol().isBlank()) {
            throw new IllegalArgumentException("desired subscription identity is incomplete");
        }
        return marketRepository.findByProviderAndSymbolAndProductType(
                        provider, desired.getSymbol(), productType)
                .orElseThrow(() -> new IllegalStateException("configured market was not found"));
    }

    private int validateMarket(OrderBookAcquisitionProperties.DesiredSubscription desired, Market market) {
        if (parseProvider(desired.getProvider()) != MarketProvider.KRAKEN
                || market.getProvider() != MarketProvider.KRAKEN) {
            throw new IllegalArgumentException("desired subscription provider must be KRAKEN");
        }
        if (parseProductType(desired.getProductType()) != InstrumentProductType.LINEAR_PERPETUAL
                || market.getProductType() != InstrumentProductType.LINEAR_PERPETUAL) {
            throw new IllegalArgumentException("desired subscription product must be LINEAR_PERPETUAL");
        }
        if (desired.getProviderSymbol() == null
                || !desired.getProviderSymbol().equals(market.getProviderSymbol())) {
            throw new IllegalArgumentException("configured provider symbol does not match market catalogue");
        }
        int depth = parseDepth(desired.getDepth());
        if (depth != 25) {
            throw new IllegalArgumentException("desired subscription depth must be 25");
        }
        if (market.getMarketState() == null || !market.getMarketState().isTradable()) {
            throw new IllegalStateException("configured market is not tradable");
        }
        return depth;
    }

    @PreDestroy
    synchronized void releaseRestoredSubscriptions() {
        for (RestoredSubscription restored : List.copyOf(restoredSubscriptions.values())) {
            try {
                subscriptionService.unsubscribe(restored.marketId(), restored.request());
            } catch (RuntimeException ignored) {
                // Shutdown must not prevent the application context from closing.
            }
        }
        restoredSubscriptions.clear();
    }

    private MarketProvider parseProvider(String value) {
        try {
            return MarketProvider.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid desired subscription provider", exception);
        }
    }

    private InstrumentProductType parseProductType(String value) {
        try {
            return InstrumentProductType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid desired subscription product", exception);
        }
    }

    private int parseDepth(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid desired subscription depth", exception);
        }
    }

    private record SubscriptionKey(
            String provider,
            String symbol,
            String productType,
            String providerSymbol,
            String depth
    ) {
        private static SubscriptionKey from(OrderBookAcquisitionProperties.DesiredSubscription desired) {
            return new SubscriptionKey(
                    desired.getProvider(), desired.getSymbol(), desired.getProductType(),
                    desired.getProviderSymbol(), desired.getDepth());
        }
    }

    private record RestoredSubscription(
            java.util.UUID marketId,
            MarketStreamRequest request
    ) {
    }
}
