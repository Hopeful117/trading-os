package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.model.PriceObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, UUID> {
    Optional<PriceObservation> findTopByMarketIdAndEffectiveAtLessThanEqualAndCapturedAtLessThanEqualOrderByEffectiveAtDescCapturedAtDescObservationIdDesc(
            UUID marketId, Instant effectiveAt, Instant capturedAt);

    Optional<PriceObservation> findTopByBaseAssetIgnoreCaseAndQuoteAssetIgnoreCaseAndEffectiveAtLessThanEqualAndCapturedAtLessThanEqualOrderByEffectiveAtDescCapturedAtDescObservationIdDesc(
            String baseAsset, String quoteAsset, Instant effectiveAt, Instant capturedAt);
}
