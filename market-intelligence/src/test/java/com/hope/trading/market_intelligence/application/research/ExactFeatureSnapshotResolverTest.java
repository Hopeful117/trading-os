package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExactFeatureSnapshotResolverTest {
    @Test
    void resolvesOnlyTheNamedSnapshotAndNeverFallsBack() {
        UUID market = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-17T10:00:00Z");
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        when(snapshot.marketId()).thenReturn(market);
        when(snapshot.evaluationCutoff()).thenReturn(cutoff);
        when(snapshot.featureProjectionVersion()).thenReturn("feature-v1");
        when(snapshot.contextFingerprint()).thenReturn("context");
        when(snapshot.fingerprint()).thenReturn("snapshot");
        when(snapshot.identityFingerprint()).thenReturn("identity");
        FeatureSnapshotRepository repository = mock(FeatureSnapshotRepository.class);
        when(repository.findByFingerprint("missing")).thenReturn(Optional.empty());
        when(repository.findByFingerprint("snapshot")).thenReturn(Optional.of(snapshot));
        var resolver = new ExactFeatureSnapshotResolver(repository);

        resolver.resolve(market, cutoff, "feature-v1", "context", "snapshot", "identity");
        assertThatThrownBy(() -> resolver.resolve(market, cutoff, "feature-v1", "context", "missing", "identity"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findByPopulation(any(), any(), any(), any(), any());
    }
}
