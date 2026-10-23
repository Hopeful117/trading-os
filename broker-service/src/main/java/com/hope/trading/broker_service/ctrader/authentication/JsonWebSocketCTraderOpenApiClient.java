package com.hope.trading.broker_service.ctrader.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class JsonWebSocketCTraderOpenApiClient implements CTraderOpenApiClient {
    private static final int APPLICATION_AUTH_REQUEST = 2100;
    private static final int APPLICATION_AUTH_RESPONSE = 2101;
    private static final int ACCOUNT_AUTH_REQUEST = 2102;
    private static final int ACCOUNT_AUTH_RESPONSE = 2103;
    private static final int ACCOUNT_LIST_REQUEST = 2149;
    private static final int ACCOUNT_LIST_RESPONSE = 2150;

    private final CTraderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JsonWebSocketCTraderOpenApiClient(CTraderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> discoverAuthorizedAccountIds(CTraderToken token) {
        try (Session session = new Session()) {
            session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                    objectMapper.createObjectNode()
                            .put("clientId", required(properties.clientId()))
                            .put("clientSecret", required(properties.clientSecret())));
            JsonNode response = session.request(ACCOUNT_LIST_REQUEST, ACCOUNT_LIST_RESPONSE,
                    objectMapper.createObjectNode().put("accessToken", token.accessToken()));
            List<String> ids = new ArrayList<>();
            for (JsonNode account : response.path("ctidTraderAccount")) {
                if (account.hasNonNull("ctidTraderAccountId")) ids.add(account.path("ctidTraderAccountId").asText());
            }
            return List.copyOf(ids);
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader account discovery failed", exception);
        }
    }

    @Override
    public void authorizeAccount(CTraderToken token, String externalAccountId) {
        try (Session session = new Session()) {
            session.request(APPLICATION_AUTH_REQUEST, APPLICATION_AUTH_RESPONSE,
                    objectMapper.createObjectNode()
                            .put("clientId", required(properties.clientId()))
                            .put("clientSecret", required(properties.clientSecret())));
            session.request(ACCOUNT_AUTH_REQUEST, ACCOUNT_AUTH_RESPONSE,
                    objectMapper.createObjectNode()
                            .put("ctidTraderAccountId", Long.parseLong(externalAccountId))
                            .put("accessToken", token.accessToken()));
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader account authorization failed", exception);
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new CTraderAuthenticationException("cTrader application is not configured");
        return value;
    }

    private final class Session implements AutoCloseable {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final WebSocket socket;

        private Session() {
            try {
                socket = httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(URI.create(properties.websocketEndpoint()), new Listener(messages))
                        .orTimeout(15, TimeUnit.SECONDS).join();
            } catch (Exception exception) {
                throw new CTraderAuthenticationException("cTrader Open API transport is unavailable", exception);
            }
        }

        private JsonNode request(int requestType, int responseType, ObjectNode payload) {
            String clientMsgId = UUID.randomUUID().toString();
            ObjectNode message = objectMapper.createObjectNode()
                    .put("clientMsgId", clientMsgId)
                    .put("payloadType", requestType)
                    .set("payload", payload);
            socket.sendText(message.toString(), true).join();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            try {
                while (System.nanoTime() < deadline) {
                    String raw = messages.poll(1, TimeUnit.SECONDS);
                    if (raw == null) continue;
                    JsonNode response = objectMapper.readTree(raw);
                    if (response.path("clientMsgId").asText().equals(clientMsgId)
                            && response.path("payloadType").asInt() == responseType) {
                        return response.path("payload");
                    }
                    if (response.path("errorCode").isTextual()
                            || response.path("payload").path("errorCode").isTextual()) {
                        throw new CTraderAuthenticationException("cTrader Open API rejected the request");
                    }
                }
                throw new CTraderAuthenticationException("cTrader Open API response timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CTraderAuthenticationException("cTrader Open API request interrupted", exception);
            } catch (CTraderAuthenticationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CTraderAuthenticationException("cTrader Open API response is invalid", exception);
            }
        }

        @Override
        public void close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final BlockingQueue<String> messages;
        private final StringBuilder text = new StringBuilder();

        private Listener(BlockingQueue<String> messages) {
            this.messages = messages;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.offer(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }
    }
}
