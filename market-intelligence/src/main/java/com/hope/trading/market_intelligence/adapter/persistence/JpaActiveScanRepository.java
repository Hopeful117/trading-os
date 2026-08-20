package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaActiveScanRepository implements ActiveScanRepository {
    private final SpringDataActiveScanRepository scans;
    private final SpringDataActiveScanMarketRepository scanMarkets;
    private final ObjectMapper mapper;

    public JpaActiveScanRepository(
            SpringDataActiveScanRepository scans,
            SpringDataActiveScanMarketRepository scanMarkets,
            ObjectMapper mapper
    ) {
        this.scans = scans;
        this.scanMarkets = scanMarkets;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ActiveScan save(ActiveScan scan) {
        JpaActiveScanEntity entity = new JpaActiveScanEntity();
        entity.scanId = scan.scanId();
        entity.actorId = scan.actorId();
        entity.accountId = scan.accountId();
        entity.idempotencyKey = scan.idempotencyKey();
        entity.requestFingerprint = scan.requestFingerprint();
        entity.status = scan.status().name();
        entity.objective = scan.objective();
        entity.payload = write(new ActiveScanPayload(scan.scopeSnapshot()));
        entity.resolvedAt = scan.resolvedAt();
        entity.createdAt = scan.createdAt();
        entity.updatedAt = scan.updatedAt();
        scans.saveAndFlush(entity);
        return scan;
    }

    @Override
    @Transactional
    public List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> markets) {
        List<JpaActiveScanMarketEntity> entities = markets.stream().map(this::toEntity).toList();
        scanMarkets.saveAllAndFlush(entities);
        return markets;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveScan> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey) {
        return scans.findByActorIdAndIdempotencyKey(actorId, idempotencyKey).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveScan> findByActorIdAndScanId(UUID actorId, UUID scanId) {
        return scans.findByActorIdAndScanId(actorId, scanId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveScan> findById(UUID scanId) {
        return scans.findById(scanId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveScanMarket> findMarketsByScanId(UUID scanId) {
        return scanMarkets.findByScanIdOrderByOrdinalAsc(scanId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveScanMarket> findMarketById(UUID scanMarketId) {
        return scanMarkets.findById(scanMarketId).map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean transitionScanStatus(
            UUID scanId,
            ActiveScanStatus expected,
            ActiveScanStatus target,
            Instant updatedAt
    ) {
        return scans.transitionStatus(scanId, expected.name(), target.name(), updatedAt) == 1;
    }

    @Override
    @Transactional
    public boolean transitionMarketStatus(
            UUID scanMarketId,
            ActiveScanMarketStatus expected,
            ActiveScanMarketStatus target,
            Instant updatedAt
    ) {
        return scanMarkets.transitionStatus(scanMarketId, expected.name(), target.name(), updatedAt) == 1;
    }

    private JpaActiveScanMarketEntity toEntity(ActiveScanMarket market) {
        JpaActiveScanMarketEntity entity = new JpaActiveScanMarketEntity();
        entity.scanMarketId = market.scanMarketId();
        entity.scanId = market.scanId();
        entity.ordinal = market.ordinal();
        entity.marketId = market.marketId();
        entity.eligible = market.eligible();
        entity.status = market.status().name();
        entity.analysisExecutionId = market.analysisExecutionId();
        entity.payload = write(new ActiveScanMarketPayload(market.exclusionReasons()));
        entity.createdAt = market.createdAt();
        entity.updatedAt = market.updatedAt();
        return entity;
    }

    private ActiveScan toDomain(JpaActiveScanEntity entity) {
        ActiveScanPayload payload = read(entity.payload, ActiveScanPayload.class);
        return ActiveScan.restore(
                entity.scanId,
                entity.actorId,
                entity.accountId,
                entity.objective,
                entity.idempotencyKey,
                entity.requestFingerprint,
                payload.scopeSnapshot(),
                ActiveScanStatus.valueOf(entity.status),
                entity.resolvedAt,
                entity.createdAt,
                entity.updatedAt
        );
    }

    private ActiveScanMarket toDomain(JpaActiveScanMarketEntity entity) {
        ActiveScanMarketPayload payload = read(entity.payload, ActiveScanMarketPayload.class);
        return ActiveScanMarket.restore(
                entity.scanMarketId,
                entity.scanId,
                entity.ordinal,
                entity.marketId,
                entity.eligible,
                payload.exclusionReasons(),
                ActiveScanMarketStatus.valueOf(entity.status),
                entity.analysisExecutionId,
                entity.createdAt,
                entity.updatedAt
        );
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize active scan persistence payload", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize active scan persistence payload", exception);
        }
    }

    private record ActiveScanPayload(ActiveScanScopeSnapshot scopeSnapshot) {
    }

    private record ActiveScanMarketPayload(List<MarketEligibilityReason> exclusionReasons) {
        private ActiveScanMarketPayload {
            exclusionReasons = List.copyOf(exclusionReasons);
        }
    }
}
