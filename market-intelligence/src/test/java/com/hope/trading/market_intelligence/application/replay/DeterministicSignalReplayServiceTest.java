package com.hope.trading.market_intelligence.application.replay;

import com.hope.trading.market_intelligence.application.port.SignalReplayResultRepository;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.replay.ReplayCostAssumptions;
import com.hope.trading.market_intelligence.domain.replay.ReplayEvidence;
import com.hope.trading.market_intelligence.domain.replay.ReplayEvidenceSample;
import com.hope.trading.market_intelligence.domain.replay.ReplayContextProvenance;
import com.hope.trading.market_intelligence.domain.replay.ReplayEvidenceStatus;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayRequest;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayResult;
import com.hope.trading.market_intelligence.strategy.application.LiveStrategyEvaluationRunner;
import com.hope.trading.market_intelligence.strategy.application.StrategyDefinitionRepository;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvidenceProvenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeterministicSignalReplayServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final StrategyId STRATEGY_ID = new StrategyId(UUID.randomUUID());
    private static final Instant CUTOFF = Instant.parse("2026-11-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CUTOFF.plusSeconds(10), ZoneOffset.UTC);

    @Test
    void identicalSemanticInputsProduceStableFingerprint() {
        SignalReplayRequest first = request(ReplayEvidenceStatus.COMPLETE, CUTOFF.minusSeconds(1));
        SignalReplayRequest second = request(ReplayEvidenceStatus.COMPLETE, CUTOFF.minusSeconds(1));

        SignalReplayResult firstResult = SignalReplayResult.from(
                first, "context", StrategyEvaluationStatus.NO_MATCH, null, List.of(), CUTOFF);
        SignalReplayResult secondResult = SignalReplayResult.from(
                second, "context", StrategyEvaluationStatus.NO_MATCH, null, List.of(), CUTOFF.plusSeconds(1));

        assertThat(firstResult.fingerprint()).isEqualTo(secondResult.fingerprint());
    }

    @Test
    void evidenceKnownAfterCutoffIsRejected() {
        assertThatThrownBy(() -> request(ReplayEvidenceStatus.COMPLETE, CUTOFF.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known-at");
    }

    @Test
    void incompleteEvidenceCannotProduceMatch() {
        StrategyDefinitionRepository definitions = mock(StrategyDefinitionRepository.class);
        LiveStrategyEvaluationRunner evaluator = mock(LiveStrategyEvaluationRunner.class);
        InMemoryResults results = new InMemoryResults();
        when(definitions.find(STRATEGY_ID, 1)).thenReturn(Optional.of(mock(StrategyDefinition.class)));
        DeterministicSignalReplayService service = new DeterministicSignalReplayService(
                definitions, evaluator, results, CLOCK);

        SignalReplayResult result = service.evaluate(
                request(ReplayEvidenceStatus.GAPPED, CUTOFF.minusSeconds(1)), context(CUTOFF));

        assertThat(result.evaluationStatus()).isEqualTo(StrategyEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.evidenceStatus()).isEqualTo(ReplayEvidenceStatus.GAPPED);
        verifyNoInteractions(evaluator);
    }

    @ParameterizedTest
    @EnumSource(value = ReplayEvidenceStatus.class, names = {
            "MISSING", "EXCLUDED", "STALE", "CONTRADICTORY", "GAPPED", "INCOMPLETE"
    })
    void everyInadmissibleEvidenceStateIsNotEvaluable(ReplayEvidenceStatus status) {
        StrategyDefinitionRepository definitions = mock(StrategyDefinitionRepository.class);
        LiveStrategyEvaluationRunner evaluator = mock(LiveStrategyEvaluationRunner.class);
        when(definitions.find(STRATEGY_ID, 1)).thenReturn(Optional.of(mock(StrategyDefinition.class)));
        DeterministicSignalReplayService service = new DeterministicSignalReplayService(
                definitions, evaluator, new InMemoryResults(), CLOCK);

        SignalReplayResult result = service.evaluate(request(status, CUTOFF.minusSeconds(1)), context(CUTOFF));

        assertThat(result.evaluationStatus()).isEqualTo(StrategyEvaluationStatus.NOT_EVALUABLE);
        verifyNoInteractions(evaluator);
    }

    @Test
    void evidenceCollectionFingerprintDoesNotUseAmbiguousDelimiters() {
        SignalReplayResult first = SignalReplayResult.from(
                requestWithEvidence(List.of(sample(CUTOFF.minusSeconds(1), "a,b"),
                        sample(CUTOFF.minusSeconds(1), "c"))),
                "context", StrategyEvaluationStatus.NO_MATCH, null, List.of(), CUTOFF);
        SignalReplayResult second = SignalReplayResult.from(
                requestWithEvidence(List.of(sample(CUTOFF.minusSeconds(1), "a"),
                        sample(CUTOFF.minusSeconds(1), "b,c"))),
                "context", StrategyEvaluationStatus.NO_MATCH, null, List.of(), CUTOFF);

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    void contextAfterCutoffIsRejectedBeforeEvaluation() {
        DeterministicSignalReplayService service = new DeterministicSignalReplayService(
                mock(StrategyDefinitionRepository.class), mock(LiveStrategyEvaluationRunner.class),
                new InMemoryResults(), CLOCK);

        assertThatThrownBy(() -> service.evaluate(
                request(ReplayEvidenceStatus.COMPLETE, CUTOFF), context(CUTOFF.plusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after replay cutoff");
    }

    @Test
    void contextWithoutStrictProvenanceIsRejected() {
        DeterministicSignalReplayService service = new DeterministicSignalReplayService(
                mock(StrategyDefinitionRepository.class), mock(LiveStrategyEvaluationRunner.class),
                new InMemoryResults(), CLOCK);
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(MARKET_ID)
                .instrument("EURUSD")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(CUTOFF)
                .build();

        assertThatThrownBy(() -> service.evaluate(
                request(ReplayEvidenceStatus.COMPLETE, CUTOFF), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provenance");
    }

    @Test
    void contextRejectsSwappedSampleNormalizationProvenance() {
        DeterministicSignalReplayService service = new DeterministicSignalReplayService(
                mock(StrategyDefinitionRepository.class), mock(LiveStrategyEvaluationRunner.class),
                new InMemoryResults(), CLOCK);
        ReplayEvidenceSample first = sample(CUTOFF, "evidence-1");
        ReplayEvidenceSample second = new ReplayEvidenceSample(
                UUID.nameUUIDFromBytes("evidence-2".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "provider", "source", "historical-ohlc-v2", first.openTime(), first.closeTime(),
                first.fetchedAt(), first.capturedAt(), first.knownAt(), null, true, false, "evidence-2");
        SignalReplayRequest request = requestWithEvidence(List.of(first, second));
        StrategyEvaluationContext context = context(CUTOFF);
        StrategyEvaluationContext mismatched = context.withReplayProvenance(new ReplayContextProvenance(
                "provider", EvidenceAcquisitionPolicy.STRICT_OBSERVED, "source-fingerprint",
                List.of(binding("evidence-1", "historical-ohlc-v2"), binding("evidence-2", "historical-ohlc-v1")), CUTOFF));

        assertThatThrownBy(() -> service.evaluate(request, mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void domainRejectsPositiveResultForInadmissibleEvidence() {
        assertThatThrownBy(() -> SignalReplayResult.from(
                request(ReplayEvidenceStatus.GAPPED, CUTOFF), "context",
                StrategyEvaluationStatus.MATCH, MatchedDirection.LONG, List.of(), CUTOFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_EVALUABLE");
    }

    @Test
    void matchPreservesDirectionAndCostAssumptions() {
        StrategyDefinitionRepository definitions = mock(StrategyDefinitionRepository.class);
        LiveStrategyEvaluationRunner evaluator = mock(LiveStrategyEvaluationRunner.class);
        InMemoryResults results = new InMemoryResults();
        StrategyDefinition definition = mock(StrategyDefinition.class);
        StrategyEvaluation evaluation = mock(StrategyEvaluation.class);
        when(definitions.find(STRATEGY_ID, 1)).thenReturn(Optional.of(definition));
        StrategyEvaluationContext evaluationContext = context(CUTOFF);
        when(evaluator.evaluate(definition, evaluationContext)).thenReturn(evaluation);
        when(evaluation.status()).thenReturn(StrategyEvaluationStatus.MATCH);
        when(evaluation.direction()).thenReturn(Optional.of(MatchedDirection.LONG));
        when(evaluation.explanation()).thenReturn("golden match");
        DeterministicSignalReplayService service = new DeterministicSignalReplayService(
                definitions, evaluator, results, CLOCK);

        SignalReplayResult result = service.evaluate(
                request(ReplayEvidenceStatus.COMPLETE, CUTOFF.minusSeconds(1)), evaluationContext);

        assertThat(result.evaluationStatus()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(result.direction()).isEqualTo(MatchedDirection.LONG);
        assertThat(result.costAssumptions().version()).isEqualTo("cost-v1");
        assertThat(result.diagnostics()).contains("evaluation=golden match");
    }

    private SignalReplayRequest request(ReplayEvidenceStatus status, Instant knownAt) {
        return requestWithEvidence(List.of(sample(knownAt, "evidence-2"), sample(knownAt, "evidence-1")), status);
    }

    private SignalReplayRequest requestWithEvidence(List<ReplayEvidenceSample> samples) {
        return requestWithEvidence(samples, ReplayEvidenceStatus.COMPLETE);
    }

    private SignalReplayRequest requestWithEvidence(List<ReplayEvidenceSample> samples, ReplayEvidenceStatus status) {
        return new SignalReplayRequest(
                MARKET_ID, "CFD", "EURUSD", "provider", StrategyApplicability.Timeframe.M15,
                CUTOFF, STRATEGY_ID, 1, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                new ReplayEvidence(status, "provider", "source-fingerprint",
                        samples, List.of("fixture")),
                new ReplayCostAssumptions("cost-v1", new BigDecimal("0.00010"), new BigDecimal("0.00002")));
    }

    private ReplayEvidenceSample sample(Instant knownAt, String fingerprint) {
        return new ReplayEvidenceSample(UUID.nameUUIDFromBytes(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "provider", "source", "historical-ohlc-v1",
                CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(600), CUTOFF.minusSeconds(500),
                CUTOFF.minusSeconds(400), knownAt, null, true, false, fingerprint);
    }

    private StrategyEvaluationContext context(Instant evaluatedAt) {
        return StrategyEvaluationContext.builder()
                .marketId(MARKET_ID)
                .instrument("EURUSD")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(evaluatedAt)
                .provenance(new StrategyEvidenceProvenance(
                        UUID.randomUUID(), UUID.randomUUID(), 1, CUTOFF,
                        "profile-v1", "rule-v1", "input-fingerprint", "assessment-fingerprint"))
                .replayProvenance(new ReplayContextProvenance(
                        "provider", EvidenceAcquisitionPolicy.STRICT_OBSERVED, "source-fingerprint",
                        List.of(binding("evidence-1", "historical-ohlc-v1"),
                                binding("evidence-2", "historical-ohlc-v1")), CUTOFF))
                .build();
    }

    private String binding(String fingerprint, String normalization) {
        return fingerprint.length() + ":" + fingerprint + normalization.length() + ":" + normalization;
    }

    private static final class InMemoryResults implements SignalReplayResultRepository {
        private final HashMap<String, SignalReplayResult> values = new HashMap<>();

        @Override
        public Optional<SignalReplayResult> findByFingerprint(String fingerprint) {
            return Optional.ofNullable(values.get(fingerprint));
        }

        @Override
        public SignalReplayResult saveIfAbsent(SignalReplayResult result) {
            return values.computeIfAbsent(result.fingerprint(), ignored -> result);
        }
    }
}
