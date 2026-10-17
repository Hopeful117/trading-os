package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.application.port.HistoricalFeeScheduleAcquirer;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.kraken.config.HistoricalFeeScheduleCaptureProperties;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class KrakenFeeScheduleCapability implements HistoricalFeeScheduleAcquirer {
    private static final String PATH = "/0/private/TradeVolume"; // NOSONAR: fixed Kraken API endpoint.
    private static final String SOURCE = "KRAKEN_TRADE_VOLUME";
    private final ProviderCredentialSession sessions;
    private final KrakenProviderClient client;
    private final HistoricalFeeScheduleCaptureProperties captureProperties;
    public KrakenFeeScheduleCapability(ProviderCredentialSession sessions, KrakenProviderClient client,
                                       HistoricalFeeScheduleCaptureProperties captureProperties) {
        this.sessions = sessions;
        this.client = client;
        this.captureProperties = captureProperties;
    }

    @Override
    public FeeScheduleObservation acquire(UUID brokerAccountId, String instrument) {
        return sessions.withCredentials(brokerAccountId, credentials -> {
            JsonNode result = client.privatePost(PATH, Map.of(
                    "pair", instrument,
                    "aclass", "derivatives",
                    "fee_schedule", "true"), credentials);
            JsonNode taker = fee(result.path("fees"), instrument);
            JsonNode maker = fee(result.path("fees_maker"), instrument);
            JsonNode activeTier = activeTier(result.path("schedules"), instrument, taker);
            if (!taker.isObject()) taker = activeTier.path("taker_fee");
            if (!maker.isObject()) maker = activeTier.path("maker_fee");
            JsonNode takerRate = rate(taker);
            JsonNode makerRate = rate(maker);
            if (!takerRate.isValueNode() || !makerRate.isValueNode()) {
                throw new BrokerProtocolException("Kraken fee schedule is missing maker or taker data");
            }
            BigDecimal takerValue = percentageFraction(takerRate);
            BigDecimal makerValue = percentageFraction(makerRate);
            String settlementAsset = asset(result.path("currency").asText());
            if (settlementAsset.isBlank()) {
                throw new BrokerProtocolException("Kraken fee schedule settlement asset is missing");
            }
            return new FeeScheduleObservation(instrument, captureProperties.getProductType(), makerValue,
                    takerValue, settlementAsset, SOURCE + ":" + instrument);
        });
    }

    private JsonNode rate(JsonNode fee) {
        return fee.isObject() ? fee.path("fee") : fee;
    }

    private JsonNode fee(JsonNode fees, String instrument) {
        if (!fees.isObject()) return fees.path(instrument);
        JsonNode exact = fees.path(instrument);
        if (exact.isObject()) return exact;
        return fees.size() == 1 ? fees.elements().next() : fees.path(instrument);
    }

    private JsonNode activeTier(JsonNode schedules, String instrument, JsonNode taker) {
        if (!schedules.isArray()) return schedules.path("missing");
        for (JsonNode schedule : schedules) {
            if (!instrument.equals(schedule.path("pair").asText()) && schedules.size() != 1) continue;
            JsonNode tiers = schedule.path("tiers");
            for (JsonNode tier : tiers) {
                if (tier.path("active").asBoolean(false)) return tier;
                if (taker.isObject() && tier.path("taker_fee").asText()
                        .equals(taker.path("fee").asText())) return tier;
            }
        }
        return schedules.path("missing");
    }

    private BigDecimal percentageFraction(JsonNode value) {
        if (!value.isTextual() && !value.isNumber()) {
            throw new BrokerProtocolException("Kraken fee schedule rate is missing");
        }
        try {
            BigDecimal percentage = new BigDecimal(value.asText());
            if (percentage.signum() < 0) throw new NumberFormatException("negative fee");
            return percentage.divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
        } catch (NumberFormatException exception) {
            throw new BrokerProtocolException("Kraken fee schedule rate is invalid");
        }
    }

    private String asset(String value) {
        if (value == null || value.isBlank()) return "";
        return value.startsWith("Z") && value.length() > 1 ? value.substring(1) : value;
    }
}
