package com.hope.trading.broker_service.kraken.credential;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3C: protects the credential-probe contract against the real
 * Kraken private-API vocabulary — each provider error maps to exactly one
 * probe outcome, transport failures degrade to UNAVAILABLE instead of being
 * treated as bad credentials, and permission grants accumulate across the
 * four probe calls.
 *
 * <p>The fluent RestClient chain is mocked at its boundary; each scripted
 * step defines what the downstream returns for one probe call.</p>
 */
class KrakenRestCredentialProbeTest {

    /** One scripted probe-call result consumed in order. */
    private sealed interface Step permits SuccessStep, ErrorStep, TransportFailureStep,
            MalformedStep, RateLimitedHttpStep, ServerErrorHttpStep {}

    private record SuccessStep() implements Step {}
    private record ErrorStep(String krakenError) implements Step {}
    private record TransportFailureStep() implements Step {}
    private record MalformedStep() implements Step {}
    private record RateLimitedHttpStep() implements Step {}
    private record ServerErrorHttpStep() implements Step {}

    private final Deque<Step> script = new ArrayDeque<>();
    private final List<String> calledPaths = new ArrayList<>();

    private KrakenRestCredentialProbe probe;
    private CredentialMaterial material;

    @BeforeEach
    void setUp() {
        probe = new KrakenRestCredentialProbe(mockRestClient());
        material = new CredentialMaterial(
                "api-key-1234".toCharArray(),
                Base64.getEncoder().encodeToString("0123456789abcdef".getBytes())
                        .toCharArray(),
                null);
    }

    @AfterEach
    void wipeSecrets() {
        material.close();
    }

    private RestClient mockRestClient() {
        RestClient restClient = mock(RestClient.class);
        var post = mock(RestClient.RequestBodyUriSpec.class);
        var bodySpec = mock(RestClient.RequestBodySpec.class);
        var responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(post);
        when(post.uri(anyString())).thenAnswer(inv -> {
            calledPaths.add(inv.getArgument(0));
            return bodySpec;
        });
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .thenReturn(bodySpec);
        when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenAnswer(inv -> nextResponse());

        return restClient;
    }

    private Object nextResponse() {
        Step step = script.poll();
        if (step == null || step instanceof SuccessStep) {
            // Kraken success bodies always carry an empty error list.
            return Map.of("error", List.of());
        }
        if (step instanceof ErrorStep(String krakenError)) {
            return Map.of("error", List.of(krakenError));
        }
        if (step instanceof TransportFailureStep) {
            throw new ResourceAccessException("connection refused");
        }
        if (step instanceof MalformedStep) {
            return "not-a-map";
        }
        if (step instanceof RateLimitedHttpStep) {
            throw httpException(429);
        }
        if (step instanceof ServerErrorHttpStep) {
            throw httpException(503);
        }
        throw new IllegalStateException("unscripted step");
    }

    private RestClientResponseException httpException(int status) {
        return new RestClientResponseException(
                "http " + status, status, "status", null, null, null);
    }

    // ---- scripting helpers --------------------------------------------------

    private void allSuccess() {
        for (int i = 0; i < 4; i++) script.add(new SuccessStep());
    }

    // ---- proofs -------------------------------------------------------------

    @Test
    void allCallsSucceedGrantingEveryReadPermission() {
        allSuccess();

        var result = probe.probe(material);

        assertThat(result.outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.SUCCESS);
        assertThat(result.granted()).containsExactlyInAnyOrder(
                BrokerPermission.READ_ACCOUNT,
                BrokerPermission.READ_BALANCES,
                BrokerPermission.READ_POSITIONS,
                BrokerPermission.READ_ORDERS,
                BrokerPermission.READ_TRADE_HISTORY);
        assertThat(calledPaths).containsExactly(
                "/0/private/Balance",
                "/0/private/OpenPositions",
                "/0/private/OpenOrders",
                "/0/private/TradesHistory");
    }

    @Test
    void invalidKeyStopsProbingImmediatelyWithNoGrants() {
        script.add(new ErrorStep("EAPI:Invalid key"));
        script.add(new SuccessStep()); // must never be consumed

        var result = probe.probe(material);

        assertThat(result.outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.INVALID_CREDENTIALS);
        assertThat(result.granted()).isEmpty();
        assertThat(calledPaths).hasSize(1)
                .as("a fatal credential failure must stop the remaining probes");
    }

    @Test
    void invalidSignatureIsAlsoACredentialFailure() {
        script.add(new ErrorStep("EAPI:Invalid signature"));

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.INVALID_CREDENTIALS);
    }

    @Test
    void permissionDeniedSkipsGrantButContinuesProbing() {
        script.add(new ErrorStep("EGeneral:Permission denied"));
        allSuccess();

        var result = probe.probe(material);

        assertThat(result.outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.PERMISSION_DENIED);
        assertThat(result.granted()).containsExactlyInAnyOrder(
                BrokerPermission.READ_POSITIONS,
                BrokerPermission.READ_ORDERS,
                BrokerPermission.READ_TRADE_HISTORY);
        assertThat(calledPaths).hasSize(4)
                .as("a soft denial must not stop the remaining probes");
    }

    @Test
    void rateLimitIsClassifiedAsTemporary() {
        script.add(new ErrorStep("EAPI:Rate limit exceeded"));

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.RATE_LIMITED);
    }

    @Test
    void throttledVariantIsAlsoRateLimiting() {
        script.add(new ErrorStep("Service:Throttled"));

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.RATE_LIMITED);
    }

    @Test
    void transportFailureIsUnavailableNotBadCredentials() {
        script.add(new TransportFailureStep());

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.UNAVAILABLE);
    }

    @Test
    void malformedProviderPayloadIsUnexpectedNotFatal() {
        script.add(new MalformedStep());

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.UNEXPECTED_RESPONSE);
    }

    @Test
    void unknownKrakenErrorIsUnexpectedResponse() {
        script.add(new ErrorStep("EOrder:Something unclassified"));

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.UNEXPECTED_RESPONSE);
    }

    @Test
    void serverErrorHttpResponseIsReportedAsUnavailable() {
        script.add(new ServerErrorHttpStep());

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.UNAVAILABLE);
    }

    @Test
    void rateLimitHttpStatusIsReportedAsRateLimited() {
        script.add(new RateLimitedHttpStep());

        assertThat(probe.probe(material).outcome())
                .isEqualTo(KrakenCredentialProbe.ProbeOutcome.RATE_LIMITED);
    }
}
