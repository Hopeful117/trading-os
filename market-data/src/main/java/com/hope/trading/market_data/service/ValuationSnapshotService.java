package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.ValuationSnapshotBatchRequest;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchRequest.PriceUse;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.ConversionLeg;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.Fact;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.FactStatus;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.SnapshotStatus;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.Source;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.PriceObservation;
import com.hope.trading.market_data.model.ValuationConversionLeg;
import com.hope.trading.market_data.model.ValuationFact;
import com.hope.trading.market_data.model.ValuationSnapshot;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.repository.PriceObservationRepository;
import com.hope.trading.market_data.repository.ValuationSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ValuationSnapshotService {
    static final String POLICY_VERSION = "CONSERVATIVE_DIRECT_FX_NO_LOOKAHEAD_V2";
    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    private final MarketRepository marketRepository;
    private final PriceObservationRepository observationRepository;
    private final ValuationSnapshotRepository snapshotRepository;
    private final Duration maxObservationAge;
    private final Clock clock;

    public ValuationSnapshotService(MarketRepository marketRepository,
                                    PriceObservationRepository observationRepository,
                                    ValuationSnapshotRepository snapshotRepository,
                                    @Value("${valuation.max-observation-age:PT5M}") Duration maxObservationAge,
                                    Clock clock) {
        this.marketRepository = marketRepository;
        this.observationRepository = observationRepository;
        this.snapshotRepository = snapshotRepository;
        this.maxObservationAge = maxObservationAge;
        this.clock = clock;
    }

    @Transactional
    public ValuationSnapshotBatchResponse create(ValuationSnapshotBatchRequest request) {
        String reportingCurrency = normalize(request.reportingCurrency());
        List<ValuationFact> facts = new ArrayList<>();
        request.instruments().forEach(instrument -> facts.add(instrumentFact(
                instrument, reportingCurrency, request.valuationTimestamp())));
        request.assets().forEach(asset -> facts.add(assetFact(
                asset, reportingCurrency, request.valuationTimestamp())));

        SnapshotStatus status = facts.stream().allMatch(fact -> fact.getStatus() == FactStatus.AVAILABLE)
                ? SnapshotStatus.COMPLETE : SnapshotStatus.INCOMPLETE;
        ValuationSnapshot snapshot = new ValuationSnapshot(
                UUID.randomUUID(), reportingCurrency, request.valuationTimestamp(), Instant.now(clock),
                POLICY_VERSION, maxObservationAge.toString(), status);
        facts.forEach(snapshot::addFact);
        snapshot = snapshotRepository.saveAndFlush(snapshot);
        return response(snapshot);
    }

    private ValuationFact instrumentFact(ValuationSnapshotBatchRequest.Instrument request,
                                         String reportingCurrency, Instant valuationTimestamp) {
        Market market = marketRepository.findById(request.marketId()).orElse(null);
        if (market == null) {
            return new ValuationFact("INSTRUMENT", request.id(), request.marketId(), null, request.priceUse(),
                    null, null, null, null, null, FactStatus.MARKET_UNAVAILABLE);
        }
        PriceObservation observation = observationRepository
                .findTopByMarketIdAndEffectiveAtLessThanEqualAndCapturedAtLessThanEqualOrderByEffectiveAtDescCapturedAtDescObservationIdDesc(
                        request.marketId(), valuationTimestamp, valuationTimestamp).orElse(null);
        if (observation == null) {
            return new ValuationFact("INSTRUMENT", request.id(), request.marketId(), null, request.priceUse(),
                    null, null, null, null, null, FactStatus.OBSERVATION_UNAVAILABLE);
        }
        BigDecimal marketPrice = price(observation, request.priceUse());
        String observationAge = observationAge(observation, valuationTimestamp);
        if (isStale(observation, valuationTimestamp)) {
            return new ValuationFact("INSTRUMENT", request.id(), request.marketId(), null, request.priceUse(),
                    observation, sourcePriceType(request.priceUse()), marketPrice, observationAge,
                    null, FactStatus.STALE);
        }
        if (marketPrice == null) {
            return new ValuationFact("INSTRUMENT", request.id(), request.marketId(), null, request.priceUse(),
                    observation, sourcePriceType(request.priceUse()), null, observationAge,
                    null, FactStatus.PRICE_UNAVAILABLE);
        }

        Conversion conversion = conversion(normalize(market.getQuoteAsset()), reportingCurrency, valuationTimestamp);
        FactStatus conversionStatus = conversion.status();
        BigDecimal value = conversionStatus == FactStatus.AVAILABLE
                ? marketPrice.multiply(conversion.rate()) : null;
        ValuationFact fact = new ValuationFact(
                "INSTRUMENT", request.id(), request.marketId(), null, request.priceUse(), observation,
                sourcePriceType(request.priceUse()), marketPrice, observationAge, value, conversionStatus);
        if (conversion.leg() != null) {
            fact.addConversionLeg(conversion.leg());
        }
        return fact;
    }

    private ValuationFact assetFact(ValuationSnapshotBatchRequest.Asset request,
                                    String reportingCurrency, Instant valuationTimestamp) {
        String asset = normalize(request.currency());
        Conversion conversion = conversion(asset, reportingCurrency, valuationTimestamp);
        ValuationFact fact = new ValuationFact(
                "ASSET", request.id(), null, asset, null, null, null, null,
                null, conversion.status() == FactStatus.AVAILABLE ? conversion.rate() : null, conversion.status());
        if (conversion.leg() != null) {
            fact.addConversionLeg(conversion.leg());
        }
        return fact;
    }

    private Conversion conversion(String from, String to, Instant valuationTimestamp) {
        if (from.equals(to)) {
            return new Conversion(FactStatus.AVAILABLE, BigDecimal.ONE,
                    new ValuationConversionLeg(from, to, BigDecimal.ONE, null, "IDENTITY", BigDecimal.ONE, null));
        }

        Optional<PriceObservation> direct = observationRepository
                .findTopByBaseAssetIgnoreCaseAndQuoteAssetIgnoreCaseAndEffectiveAtLessThanEqualAndCapturedAtLessThanEqualOrderByEffectiveAtDescCapturedAtDescObservationIdDesc(
                        from, to, valuationTimestamp, valuationTimestamp);
        if (direct.isPresent()) {
            PriceObservation source = direct.get();
            String observationAge = observationAge(source, valuationTimestamp);
            if (isStale(source, valuationTimestamp)) {
                return new Conversion(FactStatus.STALE, null,
                        new ValuationConversionLeg(from, to, null, source, "BID", source.getBid(),
                                observationAge));
            }
            if (source.getBid() == null) {
                return new Conversion(FactStatus.CONVERSION_UNAVAILABLE, null,
                        new ValuationConversionLeg(from, to, null, source, "BID", null,
                                observationAge));
            }
            return new Conversion(FactStatus.AVAILABLE, source.getBid(),
                    new ValuationConversionLeg(from, to, source.getBid(), source, "BID", source.getBid(),
                            observationAge));
        }

        Optional<PriceObservation> inverse = observationRepository
                .findTopByBaseAssetIgnoreCaseAndQuoteAssetIgnoreCaseAndEffectiveAtLessThanEqualAndCapturedAtLessThanEqualOrderByEffectiveAtDescCapturedAtDescObservationIdDesc(
                        to, from, valuationTimestamp, valuationTimestamp);
        if (inverse.isEmpty()) {
            return new Conversion(FactStatus.CONVERSION_UNAVAILABLE, null, null);
        }
        PriceObservation source = inverse.get();
        String observationAge = observationAge(source, valuationTimestamp);
        if (isStale(source, valuationTimestamp)) {
            return new Conversion(FactStatus.STALE, null,
                    new ValuationConversionLeg(from, to, null, source, "INVERSE_ASK", source.getAsk(),
                            observationAge));
        }
        if (source.getAsk() == null || source.getAsk().signum() <= 0) {
            return new Conversion(FactStatus.CONVERSION_UNAVAILABLE, null,
                    new ValuationConversionLeg(from, to, null, source, "INVERSE_ASK", source.getAsk(),
                            observationAge));
        }
        BigDecimal rate = BigDecimal.ONE.divide(source.getAsk(), DIVISION_CONTEXT);
        return new Conversion(FactStatus.AVAILABLE, rate,
                new ValuationConversionLeg(from, to, rate, source, "INVERSE_ASK", source.getAsk(),
                        observationAge));
    }

    private boolean isStale(PriceObservation observation, Instant valuationTimestamp) {
        return observation.getEffectiveAt().isBefore(valuationTimestamp.minus(maxObservationAge));
    }

    private String observationAge(PriceObservation observation, Instant valuationTimestamp) {
        return Duration.between(observation.getEffectiveAt(), valuationTimestamp).toString();
    }

    private BigDecimal price(PriceObservation observation, PriceUse priceUse) {
        return switch (priceUse) {
            case LAST -> observation.getLast();
            case CONSERVATIVE_SELL -> observation.getBid();
            case CONSERVATIVE_BUY -> observation.getAsk();
        };
    }

    private String sourcePriceType(PriceUse priceUse) {
        return switch (priceUse) {
            case LAST -> "LAST";
            case CONSERVATIVE_SELL -> "BID";
            case CONSERVATIVE_BUY -> "ASK";
        };
    }

    private ValuationSnapshotBatchResponse response(ValuationSnapshot snapshot) {
        return new ValuationSnapshotBatchResponse(
                snapshot.getSnapshotId(), snapshot.getVersion(), snapshot.getReportingCurrency(),
                snapshot.getValuationTimestamp(), snapshot.getCapturedAt(), snapshot.getPolicyVersion(),
                snapshot.getMaxObservationAge(), snapshot.getStatus(),
                snapshot.getFacts().stream().map(this::responseFact).toList());
    }

    private Fact responseFact(ValuationFact fact) {
        Source source = fact.getSourceObservationId() == null ? null : new Source(
                fact.getSourceObservationId(), fact.getSourceMarketId(), fact.getSourceProvider(),
                fact.getSourceSymbol(), fact.getSourcePriceType(), fact.getSourcePrice(), fact.getSourceEffectiveAt(),
                fact.getSourceCapturedAt(), fact.getObservationAge());
        List<ConversionLeg> legs = fact.getConversionLegs().stream().map(leg -> {
            Source legSource = leg.getSourcePriceType() == null ? null : new Source(
                    leg.getSourceObservationId(), leg.getSourceMarketId(), leg.getSourceProvider(),
                    leg.getSourceSymbol(), leg.getSourcePriceType(), leg.getSourcePrice(), leg.getSourceEffectiveAt(),
                    leg.getSourceCapturedAt(), leg.getObservationAge());
            return new ConversionLeg(leg.getFromCurrency(), leg.getToCurrency(), leg.getRate(), legSource);
        }).toList();
        return new Fact(fact.getRequestType(), fact.getRequestId(), fact.getMarketId(), fact.getAsset(),
                fact.getPriceUse(), fact.getValue(), fact.getStatus(), source, legs);
    }

    private String normalize(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private record Conversion(FactStatus status, BigDecimal rate, ValuationConversionLeg leg) {
    }
}
