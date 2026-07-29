package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
                        new ContextProvenance("test", Instant.now(), Instant.now()),
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
}
