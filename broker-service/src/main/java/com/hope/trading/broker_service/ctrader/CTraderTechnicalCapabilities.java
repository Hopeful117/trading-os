package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.TechnicalCapability;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.OrderType;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.TechnicalCapabilities;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public final class CTraderTechnicalCapabilities implements TechnicalCapability {
    private static final String PROVIDER = "CTRADER";
    private final CTraderProperties properties;
    private final Clock clock;

    public CTraderTechnicalCapabilities(CTraderProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public TechnicalCapabilities capabilities(UUID brokerAccountId, String instrument) {
        String normalized = normalize(instrument);
        if (!properties.supportedInstruments().contains(normalized)) {
            throw new IllegalArgumentException("cTrader instrument is not configured: " + instrument);
        }
        return new TechnicalCapabilities(brokerAccountId, PROVIDER, normalized, 1, clock.instant(),
                List.of(OrderType.MARKET), List.of(), List.of());
    }

    @Override
    public List<TechnicalCapabilities> capabilities(UUID brokerAccountId, List<String> instruments) {
        if (instruments == null) return List.of();
        return instruments.stream()
                .filter(instrument -> instrument != null && properties.supportedInstruments()
                        .contains(normalize(instrument)))
                .map(instrument -> capabilities(brokerAccountId, instrument))
                .toList();
    }

    private String normalize(String instrument) {
        if (instrument == null || instrument.isBlank()) return "";
        return instrument.trim().toUpperCase(Locale.ROOT);
    }
}
