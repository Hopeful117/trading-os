package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookEvidenceDependency;
import com.hope.trading.market_data.model.OrderBookDependencyState;
import com.hope.trading.market_data.model.OrderBookRetentionHold;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBookRetentionServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final UUID SEQUENCE_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-20T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-20T12:01:00Z");

    @Test
    void promotesAvailableReplayWithStableProvenance() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        when(repository.saveArtifact(any())).thenReturn(true);
        when(repository.saveDependency(any())).thenReturn(true);
        when(repository.matchesPersistedEvidence(any())).thenReturn(true);
        OrderBookEvidenceRepository evidenceRepository = mock(OrderBookEvidenceRepository.class);
        when(evidenceRepository.isCurrentEligibleSequence(any(), any(), any(Integer.class), any())).thenReturn(true);
        OrderBookRetentionService service = service(repository, evidenceRepository);

        var artifact = service.promote(replay(), "research:sample:1", "freeze research sample");

        assertThat(artifact.semanticIdentity()).isEqualTo("research:sample:1");
        assertThat(artifact.sequenceId()).isEqualTo(SEQUENCE_ID);
        assertThat(artifact.sourceEvidenceFingerprint()).isEqualTo("source-fingerprint");
        assertThat(artifact.artifactFingerprint()).hasSize(64);
        verify(repository).saveArtifact(any());
    }

    @Test
    void rejectsUnavailableReplay() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        OrderBookRetentionService service = service(repository, mock(OrderBookEvidenceRepository.class));
        OrderBookReplayResult replay = OrderBookReplayResult.unavailable(MARKET_ID, 25, CUTOFF, "missing");

        assertThatThrownBy(() -> service.promote(replay, "research:sample:1", "freeze"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available replay");
    }

    @Test
    void rejectsReplayThatUsesLookAheadData() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        OrderBookRetentionService service = service(repository, mock(OrderBookEvidenceRepository.class));
        OrderBookReplayResult replay = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, MARKET_ID, 25, SEQUENCE_ID, CUTOFF,
                CUTOFF.plusSeconds(1), CUTOFF, 0L, 2L, "KRAKEN", "PF_XBTUSD",
                "order-book-normalization-v1", "source-fingerprint", "TRUSTED",
                List.of("source-fingerprint"),
                List.of(level("100", "1")), List.of(level("101", "1")), null);

        assertThatThrownBy(() -> service.promote(replay, "research:look-ahead", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("look-ahead");
    }

    @Test
    void previewsOnlyRepositorySelectedCandidatesAndRecordsDryRun() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        when(repository.findRetirementCandidates(any())).thenReturn(List.of());
        OrderBookRetentionService service = service(repository, mock(OrderBookEvidenceRepository.class));

        var preview = service.previewRetirement();

        assertThat(preview.candidates()).isEmpty();
        assertThat(preview.operationFingerprint()).hasSize(64);
        verify(repository).recordDryRun(any(), any(), any(), any());
    }

    @Test
    void rejectsRetirementWithoutMatchingDryRun() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        var properties = new com.hope.trading.market_data.config.OrderBookRetentionProperties();
        properties.setDestructiveRetirementEnabled(true);
        when(repository.matchesDryRun(any(), any())).thenReturn(false);
        when(repository.retirementDetails(any())).thenReturn("sequence:0-0;artifacts=[];dependencies=[];holds=[]");
        OrderBookRetentionService service = new OrderBookRetentionService(repository, mock(OrderBookEvidenceRepository.class), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var candidate = new com.hope.trading.market_data.model.OrderBookRetirementCandidate(
                SEQUENCE_ID, MARKET_ID, 25,
                com.hope.trading.market_data.model.OrderBookSequenceStatus.SEALED,
                1, 0, 0, CUTOFF, CUTOFF, null);

        assertThatThrownBy(() -> service.retire("dry-run", candidate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dry-run");
    }

    @Test
    void rejectsDependencyConflictWithDifferentConsumerIdentity() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        UUID artifactId = UUID.randomUUID();
        when(repository.saveDependency(any())).thenReturn(false);
        when(repository.findDependency("dependency")).thenReturn(java.util.Optional.of(
                new OrderBookEvidenceDependency(UUID.randomUUID(), "dependency", "RESEARCH", "one", artifactId,
                        null, OrderBookDependencyState.PROTECTED, "reason", NOW, null)));
        OrderBookRetentionService service = service(repository, mock(OrderBookEvidenceRepository.class));

        assertThatThrownBy(() -> service.protectDependency("dependency", "RESEARCH", "two", artifactId,
                null, "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic identity");
    }

    @Test
    void rejectsHoldConflictWithDifferentReason() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        UUID artifactId = UUID.randomUUID();
        when(repository.saveHold(any())).thenReturn(false);
        when(repository.findHold("hold")).thenReturn(java.util.Optional.of(
                new OrderBookRetentionHold(UUID.randomUUID(), "hold", artifactId, null, "original", NOW, null)));
        OrderBookRetentionService service = service(repository, mock(OrderBookEvidenceRepository.class));

        assertThatThrownBy(() -> service.addHold("hold", artifactId, null, "changed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic identity");
    }

    @Test
    void resumesRetirementAcrossBatchesAfterServiceRecreation() {
        OrderBookRetentionRepository repository = mock(OrderBookRetentionRepository.class);
        var properties = new com.hope.trading.market_data.config.OrderBookRetentionProperties();
        properties.setDestructiveRetirementEnabled(true);
        properties.setRetirementBatchSize(1);
        when(repository.retirementDetails(any())).thenReturn("sequence:0-1;artifacts=[];dependencies=[];holds=[]");
        when(repository.matchesDryRun(any(), any())).thenReturn(true);
        when(repository.retireEvidence(any(), any(Integer.class), any())).thenReturn(1, 0);
        var candidate = new com.hope.trading.market_data.model.OrderBookRetirementCandidate(
                SEQUENCE_ID, MARKET_ID, 25,
                com.hope.trading.market_data.model.OrderBookSequenceStatus.SEALED,
                2, 0, 1, CUTOFF, CUTOFF, null);

        var first = new OrderBookRetentionService(repository, mock(OrderBookEvidenceRepository.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(first.retire("dry-run", candidate)).isEqualTo(1);
        var restarted = new OrderBookRetentionService(repository, mock(OrderBookEvidenceRepository.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(restarted.retire("dry-run", candidate)).isZero();

        verify(repository).recordPartialOperation(any(), org.mockito.ArgumentMatchers.eq(1));
        verify(repository).completeOperation(any(), any(), org.mockito.ArgumentMatchers.eq(0));
    }

    private OrderBookRetentionService service(OrderBookRetentionRepository repository) {
        return service(repository, mock(OrderBookEvidenceRepository.class));
    }

    private OrderBookRetentionService service(OrderBookRetentionRepository repository,
                                              OrderBookEvidenceRepository evidenceRepository) {
        var properties = new com.hope.trading.market_data.config.OrderBookRetentionProperties();
        properties.setArtifactSigningKey("story-0142-test-signing-key");
        return new OrderBookRetentionService(repository, evidenceRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OrderBookReplayResult replay() {
        return new OrderBookReplayResult(OrderBookReplayResult.ReplayStatus.AVAILABLE, MARKET_ID, 25,
                SEQUENCE_ID, CUTOFF, CUTOFF, CUTOFF, 0L, 2L, "KRAKEN", "PF_XBTUSD",
                "order-book-normalization-v1", "source-fingerprint", "TRUSTED",
                List.of("source-fingerprint"),
                List.of(level("100", "1")), List.of(level("101", "1")), null);
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(quantity));
    }
}
