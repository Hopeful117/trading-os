package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "market-data", contextId = "riskMarketValuationClient")
interface MarketValuationFeignClient {
    @GetMapping("/api/v1/markets")
    List<CatalogueMarket> markets();
    @PostMapping("/internal/v1/valuation-snapshots/batch")
    ValuationTransport value(@RequestBody ValuationRequest request);
}

record CatalogueMarket(UUID marketId, String provider, String symbol, String baseAsset, String quoteAsset) { }
record ValuationRequest(String reportingCurrency, Instant valuationTimestamp,
                        List<InstrumentRequest> instruments, List<AssetRequest> assets) { }
record InstrumentRequest(String id, UUID marketId, String priceUse) { }
record AssetRequest(String id, String currency) { }
record ValuationTransport(UUID snapshotId, long version, String reportingCurrency,
                          Instant valuationTimestamp, Instant capturedAt, String policyVersion,
                          String maxObservationAge, String status, List<Fact> facts) {
    record Fact(String type, String id, UUID marketId, String asset, String priceUse,
                BigDecimal value, String status, Source source, List<ConversionLeg> conversionLegs) { }
    record Source(UUID observationId, UUID marketId, String provider, String symbol,
                  String priceType, BigDecimal price, Instant effectiveAt, Instant capturedAt,
                  String observationAge) { }
    record ConversionLeg(String fromCurrency, String toCurrency, BigDecimal rate, Source source) { }
}

@Component
public final class MarketValuationClient implements MarketValuationPort {
    private final MarketValuationFeignClient client;
    private final ObjectMapper mapper;

    public MarketValuationClient(MarketValuationFeignClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Snapshot value(String reportingCurrency, Instant at,
                          List<Instrument> instruments, List<Asset> assets) {
        List<CatalogueMarket> catalogue = client.markets();
        List<InstrumentRequest> instrumentRequests = instruments.stream().map(item -> {
            List<CatalogueMarket> matches = catalogue.stream()
                    .filter(market -> market.symbol() != null && market.symbol().equalsIgnoreCase(item.symbol()))
                    .toList();
            UUID marketId = matches.size() == 1 ? matches.getFirst().marketId() : null;
            return new InstrumentRequest(item.id(), marketId, item.priceUse().name());
        }).toList();
        if (instrumentRequests.stream().anyMatch(item -> item.marketId() == null)) {
            throw new IllegalStateException("Market catalogue has a missing or ambiguous instrument");
        }
        ValuationTransport value = client.value(new ValuationRequest(reportingCurrency, at,
                instrumentRequests, assets.stream().map(a -> new AssetRequest(a.id(), a.currency())).toList()));
        try {
            List<Fact> facts = value.facts().stream().map(f -> new Fact(f.type(), f.id(), f.marketId(),
                    f.asset(), f.priceUse() == null ? null : PriceUse.valueOf(f.priceUse()), f.value(),
                    f.source() == null ? null : f.source().price(), conversionRate(f.conversionLegs()), f.status(),
                    preserve(f.source(), f.conversionLegs()))).toList();
            return new Snapshot(value.snapshotId(), value.version(), value.reportingCurrency(),
                    value.valuationTimestamp(), value.capturedAt(), value.policyVersion(),
                    value.maxObservationAge(), "COMPLETE".equals(value.status()), facts,
                    mapper.writeValueAsString(value));
        } catch (Exception failure) {
            throw new IllegalStateException("Market valuation cannot be preserved", failure);
        }
    }

    private String preserve(Object source, List<?> legs) {
        try { return mapper.writeValueAsString(java.util.Map.of("source", source == null ? "IDENTITY" : source,
                "conversionLegs", legs == null ? List.of() : legs)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private BigDecimal conversionRate(List<ValuationTransport.ConversionLeg> legs) {
        if (legs == null || legs.isEmpty() || legs.stream().anyMatch(leg -> leg.rate() == null)) return null;
        return legs.stream().map(ValuationTransport.ConversionLeg::rate)
                .reduce(BigDecimal.ONE, BigDecimal::multiply);
    }
}
