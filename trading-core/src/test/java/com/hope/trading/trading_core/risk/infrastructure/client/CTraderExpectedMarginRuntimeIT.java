package com.hope.trading.trading_core.risk.infrastructure.client;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.opentest4j.TestAbortedException;
import feign.FeignException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TRADING_OS_CTRADER_MARGIN_RUNTIME_TEST", matches = "(?i)true")
@TestPropertySource(properties = {
        "spring.cloud.openfeign.client.config.brokerRequiredMarginClient.url=${TRADING_OS_BROKER_SERVICE_BASE_URL:http://127.0.0.1:17082}",
        "security.service-jwt.secret=${TRADING_CORE_SERVICE_JWT_SECRET:}"
})
class CTraderExpectedMarginRuntimeIT {
    private static final UUID BROKER_ACCOUNT_ID =
            UUID.fromString("7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456");
    private static final String INSTRUMENT = "EUR/USD";
    private static final String EXPECTED_CURRENCY = "USD";
    private static final List<PreviewCase> PREVIEWS = List.of(
            new PreviewCase(new BigDecimal("1000"), 100000L),
            new PreviewCase(new BigDecimal("10000"), 1000000L),
            new PreviewCase(new BigDecimal("20000"), 2000000L));

    @Autowired
    private BrokerMarginFeignClient marginClient;

    @BeforeEach
    void establishAuthorizedActor() {
        String ownerValue = System.getenv("TRADING_OS_CTRADER_MARGIN_OWNER_ID");
        assumeTrue(ownerValue != null && !ownerValue.isBlank(),
                "TRADING_OS_CTRADER_MARGIN_OWNER_ID must identify the authorized account owner");

        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerValue);
        } catch (IllegalArgumentException invalidOwner) {
            throw new IllegalStateException("TRADING_OS_CTRADER_MARGIN_OWNER_ID must be a UUID", invalidOwner);
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UserDto.builder().userId(ownerId).role(Role.ROLE_USER).build(), null));
    }

    @AfterEach
    void clearAuthorizedActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewsExpectedMarginForValidatedEurUsdQuantities() {
        for (PreviewCase previewCase : PREVIEWS) {
            BrokerMarginPreview response;
            try {
                response = marginClient.preview(BROKER_ACCOUNT_ID,
                        new BrokerMarginPreviewRequest(BROKER_ACCOUNT_ID, INSTRUMENT, "BUY",
                                previewCase.canonicalQuantity(), new BigDecimal("1.10000"), null));
            } catch (FeignException failure) {
                String message = "cTrader expected-margin preview stopped after quantity "
                        + previewCase.canonicalQuantity() + ": HTTP " + failure.status() + " "
                        + failure.contentUTF8();
                System.out.println(message);
                throw new TestAbortedException(message, failure);
            }

            assertThat(response).isNotNull();
            assertThat(response.brokerAccountId()).isEqualTo(BROKER_ACCOUNT_ID);
            assertThat(response.instrument()).isEqualToIgnoringCase(INSTRUMENT);
            assertThat(response.amount()).isPositive();
            assertThat(response.currency()).isEqualTo(EXPECTED_CURRENCY);
            assertThat(response.sourceId()).isEqualTo("CTRADER:EXPECTED_MARGIN");
            assertThat(response.sourceVersion()).isPositive();
            assertThat(response.observedAt()).isNotNull();

            System.out.printf("cTrader expected margin quantity=%s providerVolume=%d amount=%s currency=%s "
                            + "source=%s observedAt=%s%n",
                    previewCase.canonicalQuantity(), previewCase.providerVolume(), response.amount(),
                    response.currency(), response.sourceId(), response.observedAt());
        }
    }

    private record PreviewCase(BigDecimal canonicalQuantity, long providerVolume) {
    }
}
