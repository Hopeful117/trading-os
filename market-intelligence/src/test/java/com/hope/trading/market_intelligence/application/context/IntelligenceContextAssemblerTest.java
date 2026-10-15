package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceContextAssemblerTest {
    @Test
    void invokesOnlyContributorsRequiredByThePlan() {
        AtomicInteger identityCalls = new AtomicInteger();
        AtomicInteger accountCalls = new AtomicInteger();
        IntelligenceContextAssembler assembler = new IntelligenceContextAssembler(List.of(
                contributor(ContextSectionType.MARKET_IDENTITY, identityCalls),
                contributor(ContextSectionType.ACCOUNT, accountCalls)
        ));

        IntelligenceContext context = assembler.assemble(
                request(),
                List.of(ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY))
        );

        assertThat(identityCalls).hasValue(1);
        assertThat(accountCalls).hasValue(0);
        assertThat(context.sections()).containsOnlyKeys(ContextSectionType.MARKET_IDENTITY);
    }

    @Test
    void representsMissingContributorExplicitly() {
        IntelligenceContext context = new IntelligenceContextAssembler(List.of())
                .assemble(
                        request(),
                        List.of(ContextRequirement.optionalPublic(ContextSectionType.NEWS))
                );

        assertThat(context.section(ContextSectionType.NEWS).orElseThrow().status())
                .isEqualTo(ContextSectionStatus.MISSING);
    }

    @Test
    void convertsContributorFailureToUnavailableWithoutLeakingTheException() {
        ContextSectionType type = ContextSectionType.TREND_CONTEXT;
        ContextContributor failing = new ContextContributor() {
            @Override
            public ContextSectionType sectionType() {
                return type;
            }

            @Override
            public ContextSection contribute(IntelligenceAnalysisRequest request) {
                throw new IllegalStateException("market-data unavailable");
            }
        };

        ContextSection section = new IntelligenceContextAssembler(List.of(failing))
                .assemble(request(), List.of(ContextRequirement.optionalPublic(type)))
                .section(type)
                .orElseThrow();

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
        assertThat(section.message()).isEqualTo("Contributor failed for TREND_CONTEXT");
    }

    @Test
    void establishesOneEvaluationCutoffAndStableFingerprint() {
        Instant cutoff = Instant.parse("2026-10-15T12:00:00Z");
        UUID analysisId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        IntelligenceContextAssembler assembler = new IntelligenceContextAssembler(
                List.of(contributor(ContextSectionType.MARKET_IDENTITY, new AtomicInteger())),
                Clock.fixed(cutoff, java.time.ZoneOffset.UTC));

        IntelligenceContext first = assembler.assemble(
                requestWith(analysisId, marketId, null),
                List.of(ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY)));
        IntelligenceContext second = assembler.assemble(
                requestWith(analysisId, marketId, cutoff),
                List.of(ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY)));

        assertThat(first.evaluationCutoff()).contains(cutoff);
        assertThat(first.quality()).isEqualTo(ContextQuality.COMPLETE);
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    void classifiesStaleContributionAsPartial() {
        ContextSectionType type = ContextSectionType.NEWS;
        ContextContributor stale = new ContextContributor() {
            @Override
            public ContextSectionType sectionType() {
                return type;
            }

            @Override
            public ContextSection contribute(IntelligenceAnalysisRequest request) {
                return new ContextSection(type, ContextSectionStatus.STALE,
                        ContextSensitivity.PUBLIC, null, null, "stale");
            }
        };

        IntelligenceContext context = new IntelligenceContextAssembler(List.of(stale))
                .assemble(request(), List.of(ContextRequirement.optionalPublic(type)));

        assertThat(context.quality()).isEqualTo(ContextQuality.PARTIAL);
    }

    @Test
    void optionalUnavailableContributionDoesNotDegradeContext() {
        ContextSectionType type = ContextSectionType.NEWS;
        IntelligenceContext context = new IntelligenceContextAssembler(List.of(
                new ContextContributor() {
                    @Override
                    public ContextSectionType sectionType() {
                        return type;
                    }

                    @Override
                    public ContextSection contribute(IntelligenceAnalysisRequest request) {
                        return new ContextSection(type, ContextSectionStatus.UNAVAILABLE,
                                ContextSensitivity.PUBLIC, null, null, "unavailable");
                    }
                }
        )).assemble(request(), List.of(ContextRequirement.optionalPublic(type)));

        assertThat(context.quality()).isEqualTo(ContextQuality.PARTIAL);
    }

    @Test
    void requiredUnavailableContributionDegradesContext() {
        ContextSectionType type = ContextSectionType.MARKET_SNAPSHOT;
        IntelligenceContext context = new IntelligenceContextAssembler(List.of(
                new ContextContributor() {
                    @Override
                    public ContextSectionType sectionType() {
                        return type;
                    }

                    @Override
                    public ContextSection contribute(IntelligenceAnalysisRequest request) {
                        return new ContextSection(type, ContextSectionStatus.UNAVAILABLE,
                                ContextSensitivity.PUBLIC, null, null, "unavailable");
                    }
                }
        ))
                .assemble(request(), List.of(ContextRequirement.requiredPublic(type)));

        assertThat(context.quality()).isEqualTo(ContextQuality.DEGRADED);
    }

    private ContextContributor contributor(
            ContextSectionType type,
            AtomicInteger calls
    ) {
        return new ContextContributor() {
            @Override
            public ContextSectionType sectionType() {
                return type;
            }

            @Override
            public ContextSection contribute(IntelligenceAnalysisRequest request) {
                calls.incrementAndGet();
                return new ContextSection(
                        type,
                        ContextSectionStatus.AVAILABLE,
                        type == ContextSectionType.ACCOUNT
                                ? ContextSensitivity.USER_PRIVATE
                                : ContextSensitivity.PUBLIC,
                        null,
                        new ContextProvenance(
                                "test",
                                Instant.parse("2026-10-15T11:59:00Z"),
                                Instant.parse("2026-10-15T11:59:30Z")),
                        null
                );
            }
        };
    }

    private IntelligenceAnalysisRequest request() {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AnalysisExecutionMode.PASSIVE,
                null
        );
    }

    private IntelligenceAnalysisRequest requestWith(UUID analysisId, UUID marketId, Instant cutoff) {
        return new IntelligenceAnalysisRequest(
                analysisId, marketId, AnalysisExecutionMode.PASSIVE,
                null, null, null, null, cutoff);
    }
}
