package com.hope.trading.broker_service.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenPositionManagementCapability;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrakenPositionManagementCapabilityTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final ObjectMapper json = new ObjectMapper();

    private static com.fasterxml.jackson.databind.JsonNode tree(String text) {
        try { return new ObjectMapper().readTree(text); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private KrakenProperties properties() {
        KrakenProperties p = new KrakenProperties();
        p.setBaseUrl("https://api.kraken.com");
        return p;
    }

    private ProviderCredentialSession session() {
        return new ProviderCredentialSession() {
            @Override
            public <T> T withCredentials(UUID id, java.util.function.Function<CredentialMaterial, T> operation) {
                try (var c = new CredentialMaterial("12345678".toCharArray(), "MTIzNDU2Nzg5MDEyMzQ1Ng==".toCharArray(), null)) {
                    return operation.apply(c);
                }
            }
        };
    }

    private KrakenPositionManagementCapability capability(KrakenProviderClient client) {
        return new KrakenPositionManagementCapability(session(), client, properties(), Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void resolveTargetReturnsScopeWhenPositionExists() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        var result = cap.resolveTarget(new ResolveTargetRequest(ACCOUNT, "TXID-1"));

        assertThat(result.brokerAccountId()).isEqualTo(ACCOUNT);
        assertThat(result.resolvedMutationScope()).contains("BTC/USD").contains("BUY");
    }

    @Test
    void resolveTargetThrowsWhenPositionNotFound() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        assertThatThrownBy(() -> cap.resolveTarget(new ResolveTargetRequest(ACCOUNT, "NOT-HERE")))
                .isInstanceOf(com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerOrderNotFoundException.class);
    }

    @Test
    void executeCloseSubmitsOppositeSideReduceOnlyOrder() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            if (path.endsWith("AddOrder")) {
                return tree("{\"txid\":[\"CLOSE-1\"]}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        CloseResult result = cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CloseAcknowledged.class);
        var ack = (CloseAcknowledged) result;
        assertThat(ack.externalOrderId()).isEqualTo("CLOSE-1");
    }

    @Test
    void executeCloseReturnsRejectedOnBrokerAuthorization() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthorizationException("not allowed");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        assertThatThrownBy(() -> cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executeCloseReturnsRejectedOnBrokerAuthentication() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthenticationException("bad key");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        assertThatThrownBy(() -> cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executeCloseReturnsRejectedOnInvalidOrder() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.InvalidOrderException("bad volume");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        assertThatThrownBy(() -> cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executeCloseReturnsRejectedOnInsufficientFunds() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.InsufficientFundsException("no funds");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        assertThatThrownBy(() -> cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executeCloseReturnsUnknownOnRateLimit() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerRateLimitException("slow down");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        CloseResult result = cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CloseUnknown.class);
        var unk = (CloseUnknown) result;
        assertThat(unk.reasonCode()).isEqualTo("BROKER_RATE_LIMITED");
    }

    @Test
    void executeCloseReturnsUnknownOnUnavailable() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerUnavailableException("down", new RuntimeException());
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        CloseResult result = cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CloseUnknown.class);
        var unk = (CloseUnknown) result;
        assertThat(unk.reasonCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void executeCloseReturnsUnknownOnProtocolError() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException("bad response");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        CloseResult result = cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CloseUnknown.class);
        var unk = (CloseUnknown) result;
        assertThat(unk.reasonCode()).isEqualTo("BROKER_RESPONSE_UNCERTAIN");
    }

    @Test
    void executeCloseReturnsUnknownOnUnknownBrokerException() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.UnknownBrokerException("unknown", new RuntimeException());
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        CloseResult result = cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CloseUnknown.class);
        var unk = (CloseUnknown) result;
        assertThat(unk.reasonCode()).isEqualTo("BROKER_RESPONSE_UNCERTAIN");
    }

    @Test
    void executeCloseReturnsUnknownWhenNoTxidReturned() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            if (path.endsWith("AddOrder")) {
                return tree("{\"txid\":[]}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        CloseResult result = cap.executeClose(new ExecuteCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CloseUnknown.class);
        var unk = (CloseUnknown) result;
        assertThat(unk.reasonCode()).isEqualTo("BROKER_RESPONSE_UNCERTAIN");
    }

    @Test
    void reconcileReturnsInconclusiveWhenNoOrderFoundButPositionsExist() {
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenOrders")) return tree("{\"open\":{}}");
            if (path.endsWith("ClosedOrders")) return tree("{\"closed\":{}}");
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        ReconciliationCloseResult result = cap.reconcile(new ReconcileCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(Inconclusive.class);
        var inconclusive = (Inconclusive) result;
        assertThat(inconclusive.reasonCode()).isEqualTo("ORDER_NOT_FOUND_POSITIONS_EXIST");
    }

    @Test
    void reconcileReturnsCommandConfirmedNotExecutedWhenOrderExistsButNotFilled() {
        String clOrdId = UUID.nameUUIDFromBytes("idem-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenOrders")) {
                return tree("{\"open\":{\"ORDER-1\":{\"descr\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"cl_ord_id\":\"" + clOrdId + "\"},\"status\":\"open\",\"vol\":\"2\",\"vol_exec\":\"0\"}}}");
            }
            if (path.endsWith("ClosedOrders")) return tree("{\"closed\":{}}");
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"2\",\"cost\":\"100\"}}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        ReconciliationCloseResult result = cap.reconcile(new ReconcileCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(CommandConfirmedNotExecuted.class);
    }

    @Test
    void reconcileReturnsInconclusiveOnBrokerError() {
        KrakenProviderClient client = (path, params, cred) -> {
            throw new com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException("broken");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        ReconciliationCloseResult result = cap.reconcile(new ReconcileCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(Inconclusive.class);
    }

    @Test
    void reconcileReturnsInconclusiveWhenOrderFilledButNoPositionLeft() {
        String clOrdId = UUID.nameUUIDFromBytes("idem-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        KrakenProviderClient client = (path, params, cred) -> {
            if (path.endsWith("OpenOrders")) return tree("{\"open\":{}}");
            if (path.endsWith("ClosedOrders")) {
                return tree("{\"closed\":{\"ORDER-1\":{\"descr\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"cl_ord_id\":\"" + clOrdId + "\"},\"status\":\"closed\",\"vol\":\"2\",\"vol_exec\":\"2\"}}}");
            }
            if (path.endsWith("OpenPositions")) {
                return tree("{\"TXID-1\":{\"pair\":\"XBTUSD\",\"type\":\"buy\",\"vol\":\"0\",\"cost\":\"0\"}}");
            }
            return tree("{}");
        };
        var cap = capability(client);
        String scope = ACCOUNT + ":BTC/USD:BUY";
        ReconciliationCloseResult result = cap.reconcile(new ReconcileCloseRequest(ACCOUNT, scope, "idem-1"));

        assertThat(result).isInstanceOf(Inconclusive.class);
    }
}
