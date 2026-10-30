package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceDependency;
import com.hope.trading.market_data.model.OrderBookDependencyState;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceSegmentStatus;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.service.OrderBookEvidenceFingerprint;
import com.hope.trading.market_data.service.OrderBookEvidenceRepository;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.service.OrderBookRetentionRepository;
import com.hope.trading.market_data.service.OrderBookReplayService;
import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers
class OrderBookRetentionPersistenceTest {
    private static final Logger LOG = LoggerFactory.getLogger(OrderBookRetentionPersistenceTest.class);
    private static final Instant KNOWN_AT = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("market_data")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MarketRepository markets;
    @Autowired
    private OrderBookEvidenceRepository evidenceRepository;
    @Autowired
    private OrderBookRetentionService retentionService;
    @Autowired
    private OrderBookRetentionRepository retentionRepository;
    @Autowired
    private OrderBookReplayService replayService;
    @Autowired
    private OrderBookRetentionProperties retentionProperties;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unknownSequenceIsNotRetirementCandidate() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));

        assertThat(retentionService.previewRetirement().candidates()).isEmpty();
    }

    @Test
    void eligibleSegmentRetirementDeletesOnlyTheSegmentRange() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        evidenceRepository.append(delta(marketId, sequenceId, 1));
        evidenceRepository.append(snapshot(marketId, sequenceId, 2, OrderBookEvidenceOrigin.CHECKPOINT));
        var segment = evidenceRepository.findSegments(10).getFirst();
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(10));

        retentionService.protectDependency("segment-release", "TEST", "segment", null, sequenceId,
                segment.segmentId(), "test segment protection");
        retentionService.releaseDependency("segment-release", "test release");

        var assessment = retentionRepository.assessSegments(10).stream()
                .filter(value -> value.segment().segmentId().equals(segment.segmentId()))
                .findFirst().orElseThrow();
        assertThat(assessment.eligible()).isTrue();

        assertThat(retentionRepository.retireEvidenceSegment(assessment.segment(), 100,
                KNOWN_AT.plusSeconds(20))).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence
                 WHERE sequence_id = ? AND sequence_ordinal BETWEEN 0 AND 1
                """, Long.class, sequenceId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence
                 WHERE sequence_id = ? AND sequence_ordinal = 2
                """, Long.class, sequenceId)).isEqualTo(1);
        assertThat(evidenceRepository.findSegments(10).stream()
                .filter(value -> value.segmentId().equals(segment.segmentId()))
                .findFirst().orElseThrow().status()).isEqualTo(OrderBookEvidenceSegmentStatus.RETIRED);
    }

    @Test
    void protectedDependencyExcludesSealedSequenceUntilReleased() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence evidence = evidence(marketId, sequenceId);
        evidenceRepository.append(evidence);
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));

        OrderBookReplayResult replay = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, 25, sequenceId, KNOWN_AT,
                KNOWN_AT.minusSeconds(1), KNOWN_AT, 0L, 0L, "KRAKEN", "PF_XBTUSD",
                 OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, aggregateFingerprint(evidence.contentFingerprint()), "TRUSTED",
                 List.of(evidence.contentFingerprint()),
                 evidence.bids(), evidence.asks(), null);
        retentionService.promote(replay, "research:sample:retention-test", "test");

        assertThat(retentionService.previewRetirement().candidates()).isEmpty();

        assertThat(retentionService.releaseDependency("artifact:research:sample:retention-test", "test release"))
                .isTrue();
        assertThatThrownBy(() -> retentionService.promote(
                replay, "research:sample:retention-test", "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicitly released");
        var preview = retentionService.previewRetirement();
        assertThat(preview.candidates())
                .extracting(candidate -> candidate.sequenceId())
                .containsExactly(sequenceId);

        retentionProperties.setDestructiveRetirementEnabled(true);
        assertThat(retentionService.retire(preview.operationFingerprint(), preview.candidates().getFirst()))
                .isEqualTo(1);
        assertThat(retentionService.previewRetirement().candidates()).isEmpty();
    }

    @Test
    void rejectsProtectionAfterSequenceEvidenceWasRetired() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));

        retentionService.protectDependency("released-before-retirement", "TEST", "sample", null,
                sequenceId, "test protection");
        assertThat(retentionService.releaseDependency("released-before-retirement", "test release")).isTrue();

        var preview = retentionService.previewRetirement();
        retentionProperties.setDestructiveRetirementEnabled(true);
        assertThat(retentionService.retire(preview.operationFingerprint(), preview.candidates().getFirst()))
                .isEqualTo(1);

        assertThatThrownBy(() -> retentionService.protectDependency(
                "late-protection", "TEST", "sample", null, sequenceId, "late protection"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without retained evidence");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dependencyRetirementRaceNeverLeavesProtectedReferenceToDeletedEvidence() throws Exception {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));
        retentionService.protectDependency("released-race-dependency", "TEST", "sample", null,
                sequenceId, "released dependency");
        retentionService.releaseDependency("released-race-dependency", "prepare retirement");

        var preview = retentionService.previewRetirement();
        retentionProperties.setDestructiveRetirementEnabled(true);
        var executor = Executors.newFixedThreadPool(2);
        try (var connection = jdbc.getDataSource().getConnection();
             var statement = connection.prepareStatement("""
                     SELECT sequence_id
                       FROM historical_order_book_sequence
                      WHERE sequence_id = ?
                      FOR UPDATE
                     """)) {
            connection.setAutoCommit(false);
            statement.setObject(1, sequenceId);
            statement.executeQuery().close();

            var dependencyFuture = executor.submit(() -> {
                try {
                    retentionService.protectDependency("race-dependency", "TEST", "sample", null,
                            sequenceId, "concurrent dependency");
                    return true;
                } catch (RuntimeException ignored) {
                    return false;
                }
            });
            var retirementFuture = executor.submit(() -> {
                try {
                    return retentionService.retire(preview.operationFingerprint(), preview.candidates().getFirst());
                } catch (RuntimeException ignored) {
                    return -1;
                }
            });

            Thread.sleep(200);
            connection.commit();

            boolean protectedDependency = dependencyFuture.get(5, TimeUnit.SECONDS);
            int retired = retirementFuture.get(5, TimeUnit.SECONDS);
            long evidenceCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM historical_order_book_evidence WHERE sequence_id = ?",
                    Long.class, sequenceId);

            assertThat(protectedDependency && retired > 0).isFalse();
            if (protectedDependency) {
                assertThat(evidenceCount).isPositive();
            }
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM historical_order_book_evidence_dependency WHERE dependency_key IN (?, ?)",
                    "released-race-dependency", "race-dependency");
            jdbc.update("DELETE FROM historical_order_book_evidence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM historical_order_book_sequence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM markets WHERE market_id = ?", marketId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void holdRetirementRaceNeverLeavesActiveHoldForDeletedEvidence() throws Exception {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        evidenceRepository.sealActiveSequences(marketId, 25, KNOWN_AT.plusSeconds(1));
        retentionService.protectDependency("released-race-hold", "TEST", "sample", null,
                sequenceId, "released dependency");
        retentionService.releaseDependency("released-race-hold", "prepare retirement");

        var preview = retentionService.previewRetirement();
        retentionProperties.setDestructiveRetirementEnabled(true);
        var executor = Executors.newFixedThreadPool(2);
        try (var connection = jdbc.getDataSource().getConnection();
             var statement = connection.prepareStatement("""
                     SELECT sequence_id
                       FROM historical_order_book_sequence
                      WHERE sequence_id = ?
                      FOR UPDATE
                     """)) {
            connection.setAutoCommit(false);
            statement.setObject(1, sequenceId);
            statement.executeQuery().close();

            var holdFuture = executor.submit(() -> {
                try {
                    retentionService.addHold("race-hold", null, sequenceId, "concurrent hold");
                    return true;
                } catch (RuntimeException ignored) {
                    return false;
                }
            });
            var retirementFuture = executor.submit(() -> {
                try {
                    return retentionService.retire(preview.operationFingerprint(), preview.candidates().getFirst());
                } catch (RuntimeException ignored) {
                    return -1;
                }
            });

            Thread.sleep(200);
            connection.commit();

            boolean activeHold = holdFuture.get(5, TimeUnit.SECONDS);
            int retired = retirementFuture.get(5, TimeUnit.SECONDS);
            long evidenceCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM historical_order_book_evidence WHERE sequence_id = ?",
                    Long.class, sequenceId);

            assertThat(activeHold && retired > 0).isFalse();
            if (activeHold) {
                assertThat(evidenceCount).isPositive();
            }
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM historical_order_book_retention_hold WHERE hold_key = ?", "race-hold");
            jdbc.update("DELETE FROM historical_order_book_evidence_dependency WHERE dependency_key = ?",
                    "released-race-hold");
            jdbc.update("DELETE FROM historical_order_book_evidence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM historical_order_book_sequence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM markets WHERE market_id = ?", marketId);
        }
    }

    @Test
    void promotionRejectsPayloadThatDoesNotMatchCanonicalEvidence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence persisted = evidence(marketId, sequenceId);
        evidenceRepository.append(persisted);
        OrderBookReplayResult forged = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, 25, sequenceId, KNOWN_AT,
                KNOWN_AT.minusSeconds(1), KNOWN_AT, 0L, 0L, "KRAKEN", "PF_XBTUSD",
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION,
                aggregateFingerprint(persisted.contentFingerprint()), "TRUSTED",
                List.of(persisted.contentFingerprint()), List.of(level("999", "1")), persisted.asks(), null);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                retentionService.promote(forged, "research:sample:forged", "test forged payload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match persisted evidence");
    }

    @Test
    void promotionRejectsMixedOriginWhenCanonicalEvidenceHasOnlyOneOrigin() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence persisted = evidence(marketId, sequenceId);
        evidenceRepository.append(persisted);
        OrderBookReplayResult forged = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, 25, sequenceId, KNOWN_AT,
                KNOWN_AT.minusSeconds(1), KNOWN_AT, 0L, 0L, "KRAKEN", "PF_XBTUSD",
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION,
                aggregateFingerprint(persisted.contentFingerprint()), "TRUSTED",
                List.of(persisted.contentFingerprint()), persisted.bids(), persisted.asks(), null,
                OrderBookEvidenceOrigin.MIXED);

        assertThatThrownBy(() -> retentionService.promote(forged, "research:sample:fake-mixed", "test mixed origin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match persisted evidence");
    }

    @Test
    void replaySelectsTheCurrentActiveSequenceWithoutUsingInvalidatedHistory() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID invalidatedSequenceId = UUID.randomUUID();
        UUID activeSequenceId = UUID.randomUUID();
        Instant cutoff = KNOWN_AT.plusSeconds(30);

        evidenceRepository.append(evidence(marketId, invalidatedSequenceId));
        evidenceRepository.invalidateActiveSequences(marketId, 25, "test invalidation", KNOWN_AT);

        OrderBookEvidence activeEvidence = evidence(marketId, activeSequenceId);
        OrderBookEvidence activeAtCutoff = new OrderBookEvidence(activeEvidence.evidenceId(), activeEvidence.marketId(),
                activeEvidence.provider(), activeEvidence.symbol(), activeEvidence.providerSymbol(),
                activeEvidence.depth(), activeEvidence.evidenceType(), activeEvidence.origin(),
                activeEvidence.sourceStartOrdinal(), activeEvidence.sourceEndOrdinal(), activeEvidence.sequenceId(),
                activeEvidence.sequenceOrdinal(), activeEvidence.providerSequence(), cutoff, cutoff,
                activeEvidence.bids(), activeEvidence.asks(), activeEvidence.checksum(),
                activeEvidence.normalizationVersion(), "pending");
        activeAtCutoff = new OrderBookEvidence(activeAtCutoff.evidenceId(), activeAtCutoff.marketId(),
                activeAtCutoff.provider(), activeAtCutoff.symbol(), activeAtCutoff.providerSymbol(),
                activeAtCutoff.depth(), activeAtCutoff.evidenceType(), activeAtCutoff.origin(),
                activeAtCutoff.sourceStartOrdinal(), activeAtCutoff.sourceEndOrdinal(), activeAtCutoff.sequenceId(),
                activeAtCutoff.sequenceOrdinal(), activeAtCutoff.providerSequence(), activeAtCutoff.occurredAt(),
                activeAtCutoff.knownAt(), activeAtCutoff.bids(), activeAtCutoff.asks(), activeAtCutoff.checksum(),
                activeAtCutoff.normalizationVersion(), OrderBookEvidenceFingerprint.fingerprint(activeAtCutoff));
        evidenceRepository.append(activeAtCutoff);

        var result = replayService.replay(marketId, 25, cutoff);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        assertThat(result.sequenceId()).isEqualTo(activeSequenceId);
    }

    @Test
    void invalidateSequenceOnlyInvalidatesTheRequestedSequence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID invalidatedSequenceId = UUID.randomUUID();
        UUID preservedSequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, invalidatedSequenceId));
        evidenceRepository.append(evidence(marketId, preservedSequenceId));

        evidenceRepository.invalidateSequence(invalidatedSequenceId, "targeted invalidation", KNOWN_AT);

        OrderBookReplayResult result = replayService.replay(marketId, 25, KNOWN_AT);

        assertThat(result.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        assertThat(result.sequenceId()).isEqualTo(preservedSequenceId);
    }

    @Test
    void replayReadsMatchingPromotedArtifactBeforeCanonicalEvidence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));

        OrderBookReplayResult canonical = replayService.replay(marketId, 25, KNOWN_AT);
        retentionService.promote(canonical, "research:sample:artifact-read", "test artifact read");
        assertThat(retentionService.isReusableArtifact("research:sample:artifact-read")).isTrue();
        assertThat(retentionRepository.findDependency("artifact:research:sample:artifact-read"))
                .get().extracting(OrderBookEvidenceDependency::state)
                .isEqualTo(OrderBookDependencyState.PROTECTED);

        OrderBookReplayResult replayed = replayService.replay(
                marketId, 25, KNOWN_AT, "research:sample:artifact-read");

        assertThat(replayed.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
        assertThat(replayed.sequenceId()).isEqualTo(sequenceId);
        assertThat(replayed.sourceEvidenceFingerprint())
                .isEqualTo(canonical.sourceEvidenceFingerprint());
        assertThat(replayed.bids()).isEqualTo(canonical.bids());
        assertThat(replayed.asks()).isEqualTo(canonical.asks());
    }

    @Test
    void artifactFirstReplayMatchesCanonicalReplayAndMeasuresLatencyBenefit() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        for (int ordinal = 1; ordinal <= 10000; ordinal++) {
            evidenceRepository.append(delta(marketId, sequenceId, ordinal));
        }
        Instant cutoff = KNOWN_AT.plusSeconds(10000);
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, cutoff);
        retentionService.promote(canonical, "research:sample:performance", "performance measurement");

        replayService.replay(marketId, 25, cutoff, "research:sample:performance");
        long canonicalNanos = medianNanos(() -> replayService.replay(marketId, 25, cutoff));
        long artifactNanos = medianNanos(
                () -> replayService.replay(marketId, 25, cutoff, "research:sample:performance"));
        OrderBookReplayResult optimized = replayService.replay(
                marketId, 25, cutoff, "research:sample:performance");

        assertThat(optimized).usingRecursiveComparison().isEqualTo(canonical);
        assertThat(artifactNanos).isLessThan(canonicalNanos);
        LOG.info("order-book replay benchmark evidenceRows={} canonicalMicros={} artifactMicros={} speedup={}",
                10001, canonicalNanos / 1_000, artifactNanos / 1_000,
                String.format("%.2fx", (double) canonicalNanos / artifactNanos));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidationWaitsForPostgresReplayEligibilityLock() throws Exception {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));

        CountDownLatch lockAcquired = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var connection = jdbc.getDataSource().getConnection();
             var statement = connection.prepareStatement("""
                     SELECT sequence_id
                       FROM historical_order_book_sequence
                      WHERE sequence_id = ?
                      FOR SHARE
                     """)) {
            connection.setAutoCommit(false);
            statement.setObject(1, sequenceId);
            statement.executeQuery().close();
            lockAcquired.countDown();

            var invalidation = executor.submit(() -> {
                evidenceRepository.invalidateSequence(sequenceId, "concurrency test", KNOWN_AT.plusSeconds(1));
                return true;
            });

            assertThat(lockAcquired.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(invalidation).isNotDone();
            connection.commit();
            assertThat(invalidation.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM historical_order_book_sequence WHERE sequence_id = ?
                    """, String.class, sequenceId)).isEqualTo("INVALIDATED");
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM historical_order_book_evidence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM historical_order_book_sequence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM markets WHERE market_id = ?", marketId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void artifactReplayAndInvalidationAreConsistentWhenRunConcurrently() throws Exception {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        Instant cutoff = KNOWN_AT;
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, cutoff);
        retentionService.promote(canonical, "research:sample:concurrent", "concurrency test");

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var replayCompletedAt = new AtomicLong();
        try {
            var replayFuture = executor.submit(() -> {
                start.await();
                OrderBookReplayResult result = replayService.replay(
                        marketId, 25, cutoff, "research:sample:concurrent");
                replayCompletedAt.set(System.nanoTime());
                return result;
            });
            var invalidationFuture = executor.submit(() -> {
                start.await();
                evidenceRepository.invalidateSequence(sequenceId, "concurrent invalidation", KNOWN_AT.plusSeconds(1));
                return System.nanoTime();
            });
            start.countDown();

            OrderBookReplayResult result = replayFuture.get(5, TimeUnit.SECONDS);
            long invalidatedAt = invalidationFuture.get(5, TimeUnit.SECONDS);
            if (invalidatedAt < replayCompletedAt.get()) {
                assertThat(result.status()).isNotEqualTo(OrderBookReplayResult.ReplayStatus.AVAILABLE);
            } else {
                assertThat(result).usingRecursiveComparison().isEqualTo(canonical);
            }
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM historical_order_book_evidence_dependency WHERE artifact_id IN "
                    + "(SELECT artifact_id FROM historical_order_book_replay_artifact WHERE semantic_identity = ?)",
                    "research:sample:concurrent");
            jdbc.update("DELETE FROM historical_order_book_replay_artifact WHERE semantic_identity = ?",
                    "research:sample:concurrent");
            jdbc.update("DELETE FROM historical_order_book_evidence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM historical_order_book_sequence WHERE sequence_id = ?", sequenceId);
            jdbc.update("DELETE FROM markets WHERE market_id = ?", marketId);
        }
    }

    @Test
    void replayDoesNotServeArtifactAfterItsSequenceIsInvalidated() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, KNOWN_AT);
        retentionService.promote(canonical, "research:sample:invalidated", "test invalidation");

        evidenceRepository.invalidateActiveSequences(marketId, 25, "test invalidation", KNOWN_AT.plusSeconds(1));

        assertThatThrownBy(() -> retentionService.promote(
                canonical, "research:sample:invalidated-rebuild", "test invalidation rebuild"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer eligible");

        OrderBookReplayResult replayed = replayService.replay(
                marketId, 25, KNOWN_AT, "research:sample:invalidated");

        assertThat(replayed.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.UNAVAILABLE);
    }

    @Test
    void replayDoesNotServeArtifactFromSupersededSequence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID originalSequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, originalSequenceId));
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, KNOWN_AT);
        retentionService.promote(canonical, "research:sample:superseded", "test supersession");

        UUID replacementSequenceId = UUID.randomUUID();
        evidenceRepository.appendSnapshotAsNewSequence(
                evidence(marketId, replacementSequenceId), KNOWN_AT.plusSeconds(1));

        OrderBookReplayResult replayed = replayService.replay(
                marketId, 25, KNOWN_AT, "research:sample:superseded");

        assertThat(replayed.status()).isEqualTo(OrderBookReplayResult.ReplayStatus.UNAVAILABLE);
        assertThat(replayed.sequenceId()).isNotEqualTo(originalSequenceId);
    }

    @Test
    void replayFallsBackWhenArtifactPayloadFingerprintIsCorrupted() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, KNOWN_AT);
        retentionService.promote(canonical, "research:sample:corrupted", "test corruption");
        jdbc.update("""
                UPDATE historical_order_book_replay_artifact
                   SET bids_payload = ?
                 WHERE semantic_identity = ?
                """, "999:1", "research:sample:corrupted");

        OrderBookReplayResult replayed = replayService.replay(
                marketId, 25, KNOWN_AT, "research:sample:corrupted");

        assertThat(replayed.bids()).isEqualTo(canonical.bids());
    }

    @Test
    void replayFallsBackWhenArtifactSchemaVersionIsUnknown() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, KNOWN_AT);
        retentionService.promote(canonical, "research:sample:unknown-schema", "test schema");
        jdbc.update("""
                UPDATE historical_order_book_replay_artifact
                   SET artifact_schema_version = 'unknown-v99'
                 WHERE semantic_identity = ?
                """, "research:sample:unknown-schema");

        OrderBookReplayResult replayed = replayService.replay(
                marketId, 25, KNOWN_AT, "research:sample:unknown-schema");

        assertThat(replayed.bids()).isEqualTo(canonical.bids());
    }

    @Test
    void promotionReportsDeterministicConflictForInvalidatedIdentity() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        evidenceRepository.append(evidence(marketId, sequenceId));
        OrderBookReplayResult canonical = replayService.replay(marketId, 25, KNOWN_AT);
        retentionService.promote(canonical, "research:sample:rebuild", "test rebuild");
        evidenceRepository.invalidateActiveSequences(marketId, 25, "test invalidation", KNOWN_AT.plusSeconds(1));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                retentionService.promote(canonical, "research:sample:rebuild", "test rebuild")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer eligible");
    }

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId) {
        OrderBookEvidence pending = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookEvidenceType.SNAPSHOT, OrderBookEvidenceOrigin.PROVIDER, null, null,
                sequenceId, 0, 100L, KNOWN_AT.minusSeconds(1), KNOWN_AT,
                List.of(level("100", "1")), List.of(level("101", "1")), null,
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new OrderBookEvidence(pending.evidenceId(), pending.marketId(), pending.provider(), pending.symbol(),
                pending.providerSymbol(), pending.depth(), pending.evidenceType(), pending.origin(),
                pending.sourceStartOrdinal(), pending.sourceEndOrdinal(), pending.sequenceId(),
                pending.sequenceOrdinal(), pending.providerSequence(), pending.occurredAt(), pending.knownAt(),
                pending.bids(), pending.asks(), pending.checksum(), pending.normalizationVersion(),
                OrderBookEvidenceFingerprint.fingerprint(pending));
    }

    private OrderBookEvidence snapshot(UUID marketId, UUID sequenceId, long ordinal,
                                       OrderBookEvidenceOrigin origin) {
        OrderBookEvidence pending = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookEvidenceType.SNAPSHOT, origin, null, null,
                sequenceId, ordinal, 100L + ordinal, KNOWN_AT.plusSeconds(ordinal), KNOWN_AT.plusSeconds(ordinal),
                List.of(level("100", "1")), List.of(level("101", "1")), null,
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new OrderBookEvidence(pending.evidenceId(), pending.marketId(), pending.provider(), pending.symbol(),
                pending.providerSymbol(), pending.depth(), pending.evidenceType(), pending.origin(),
                pending.sourceStartOrdinal(), pending.sourceEndOrdinal(), pending.sequenceId(),
                pending.sequenceOrdinal(), pending.providerSequence(), pending.occurredAt(), pending.knownAt(),
                pending.bids(), pending.asks(), pending.checksum(), pending.normalizationVersion(),
                OrderBookEvidenceFingerprint.fingerprint(pending));
    }

    private OrderBookEvidence delta(UUID marketId, UUID sequenceId, long ordinal) {
        OrderBookEvidence pending = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookEvidenceType.DELTA, OrderBookEvidenceOrigin.PROVIDER, null, null,
                sequenceId, ordinal, 100L + ordinal, KNOWN_AT.plusSeconds(ordinal), KNOWN_AT.plusSeconds(ordinal),
                List.of(level("100", "1")), List.of(), null,
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new OrderBookEvidence(pending.evidenceId(), pending.marketId(), pending.provider(), pending.symbol(),
                pending.providerSymbol(), pending.depth(), pending.evidenceType(), pending.origin(),
                pending.sourceStartOrdinal(), pending.sourceEndOrdinal(), pending.sequenceId(),
                pending.sequenceOrdinal(), pending.providerSequence(), pending.occurredAt(), pending.knownAt(),
                pending.bids(), pending.asks(), pending.checksum(), pending.normalizationVersion(),
                OrderBookEvidenceFingerprint.fingerprint(pending));
    }

    private long medianNanos(java.util.function.Supplier<OrderBookReplayResult> replay) {
        long[] samples = new long[5];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            replay.get();
            samples[index] = System.nanoTime() - started;
        }
        java.util.Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(quantity));
    }

    private String aggregateFingerprint(String fingerprint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Market market() {
        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .providerSymbol("PF_XBTUSD")
                .baseAsset("BTC")
                .quoteAsset("USD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL)
                .marketConstraints(MarketConstraints.builder().build())
                .marketState(MarketState.builder().tradingStatus(TradingStatus.OPEN)
                        .tradable(true).lastUpdated(KNOWN_AT.minusSeconds(60)).build())
                .build();
    }
}
