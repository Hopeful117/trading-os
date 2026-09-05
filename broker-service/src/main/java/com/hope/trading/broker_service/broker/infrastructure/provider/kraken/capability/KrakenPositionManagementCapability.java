package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.PositionManagementCapability;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenProviderClient;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenAssetNormalizer;
import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class KrakenPositionManagementCapability implements PositionManagementCapability {
    private static final Logger log = LoggerFactory.getLogger(KrakenPositionManagementCapability.class);

    private final ProviderCredentialSession sessions;
    private final KrakenProviderClient client;
    private final KrakenProperties properties;
    private final Clock clock;

    public KrakenPositionManagementCapability(ProviderCredentialSession sessions, KrakenProviderClient client, KrakenProperties properties, Clock clock) {
        this.sessions = sessions; this.client = client; this.properties = properties; this.clock = clock;
    }

    @Override
    public ResolvedPositionCloseTarget resolveTarget(ResolveTargetRequest request) {
        return sessions.withCredentials(request.brokerAccountId(), c -> {
            JsonNode result = client.privatePost("/0/private/OpenPositions", Map.of(), c);
            Optional<JsonNode> position = findPositionByTxid(result, request.brokerPositionReference());
            if (position.isEmpty()) {
                throw new BrokerOrderNotFoundException("Position reference not found: " + request.brokerPositionReference());
            }
            String pair = position.get().path("pair").asText();
            String instrument = KrakenAssetNormalizer.pair(pair).instrument();
            String scope = buildMutationScope(request.brokerAccountId(), instrument, position.get());
            log.debug("Resolved mutation scope: {}", scope);
            return new ResolvedPositionCloseTarget(request.brokerAccountId(), scope);
        });
    }

    @Override
    public CloseResult executeClose(ExecuteCloseRequest request) {
        try {
            return sessions.withCredentials(request.brokerAccountId(), c -> {
                JsonNode positions = client.privatePost("/0/private/OpenPositions", Map.of(), c);
                ResolvedScopeData scopeData = validateAndExtractScope(positions, request.resolvedMutationScope());

                String oppositeSide = scopeData.side().equals("buy") ? "sell" : "buy";
                BigDecimal aggregateVolume = scopeData.aggregateVolume();
                String clOrdId = clientOrderId(request.idempotencyKey());

                log.info("Executing Kraken full exposure close: pair={}, side={}, volume={}, reduce_only=true, cl_ord_id={}",
                        scopeData.pair(), oppositeSide, aggregateVolume.toPlainString(), clOrdId);

                Map<String, String> body = new LinkedHashMap<>();
                body.put("pair", scopeData.pair());
                body.put("type", oppositeSide);
                body.put("ordertype", "market");
                body.put("volume", aggregateVolume.toPlainString());
                body.put("reduce_only", "true");
                body.put("cl_ord_id", clOrdId);

                JsonNode result = client.privatePost("/0/private/AddOrder", body, c);
                JsonNode txids = result.path("txid");
                if (!txids.isArray() || txids.isEmpty()) {
                    throw new BrokerProtocolException("Kraken did not return an order id");
                }
                String orderId = txids.get(0).asText();
                return new CloseAcknowledged(orderId, clOrdId);
            });
        } catch (BrokerAuthorizationException | InvalidOrderException | InsufficientFundsException e) {
            return new CloseRejected(null, safeCode(e));
        } catch (BrokerAuthenticationException e) {
            return new CloseRejected(null, "BROKER_AUTHENTICATION_FAILED");
        } catch (BrokerRateLimitException e) {
            return new CloseUnknown("BROKER_RATE_LIMITED");
        } catch (BrokerUnavailableException e) {
            return new CloseUnknown("PROVIDER_UNAVAILABLE");
        } catch (BrokerProtocolException | UnknownBrokerException e) {
            return new CloseUnknown("BROKER_RESPONSE_UNCERTAIN");
        }
    }

    @Override
    public ReconciliationCloseResult reconcile(ReconcileCloseRequest request) {
        try {
            return sessions.withCredentials(request.brokerAccountId(), c -> {
                String clOrdId = clientOrderId(request.idempotencyKey());

                List<OrderSnapshot> matchingOrders = readOrders(c, clOrdId);
                if (!matchingOrders.isEmpty()) {
                    OrderSnapshot order = matchingOrders.get(0);
                    if (order.status() == OrderStatus.FILLED || order.status() == OrderStatus.PARTIALLY_FILLED) {
                        JsonNode positions = client.privatePost("/0/private/OpenPositions", Map.of(), c);
                        ResolvedScopeData scopeData = validateAndExtractScope(positions, request.resolvedMutationScope());
                        if (scopeData.aggregateVolume().signum() == 0) {
                            return new ExposureConfirmedAbsent();
                        }
                    }
                }

                JsonNode positions = client.privatePost("/0/private/OpenPositions", Map.of(), c);
                ResolvedScopeData scopeData = validateAndExtractScope(positions, request.resolvedMutationScope());
                if (scopeData.aggregateVolume().signum() == 0) {
                    return new ExposureConfirmedAbsent();
                }

                if (!matchingOrders.isEmpty()) {
                    return new CommandConfirmedNotExecuted();
                }

                return new Inconclusive("ORDER_NOT_FOUND_POSITIONS_EXIST");
            });
        } catch (BrokerTechnicalException e) {
            return new Inconclusive(safeCode(e));
        }
    }

    private Optional<JsonNode> findPositionByTxid(JsonNode openPositions, String txid) {
        if (openPositions.has(txid)) {
            return Optional.of(openPositions.get(txid));
        }
        return Optional.empty();
    }

    private String buildMutationScope(UUID brokerAccountId, String instrument, JsonNode position) {
        String side = position.path("type").asText();
        return brokerAccountId + ":" + instrument + ":" + side.toUpperCase();
    }

    private record ResolvedScopeData(String pair, String side, BigDecimal aggregateVolume) {}

    private ResolvedScopeData validateAndExtractScope(JsonNode openPositions, String resolvedMutationScope) {
        String[] parts = resolvedMutationScope.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid resolvedMutationScope format: " + resolvedMutationScope);
        }
        String expectedAccount = parts[0];
        String expectedInstrument = parts[1];
        String expectedSide = parts[2];

        BigDecimal aggregate = BigDecimal.ZERO;
        String foundPair = null;
        String foundSide = null;

        Iterator<Map.Entry<String, JsonNode>> fields = openPositions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode p = entry.getValue();
            String pair = p.path("pair").asText();
            String instrument = KrakenAssetNormalizer.pair(pair).instrument();
            String type = p.path("type").asText().toUpperCase();

            if (instrument.equals(expectedInstrument) && type.equals(expectedSide)) {
                BigDecimal quantity = new BigDecimal(p.path("vol").asText("0"));
                aggregate = aggregate.add(quantity);
                foundPair = pair;
                foundSide = type.toLowerCase();
            }
        }

        if (aggregate.signum() == 0) {
            throw new BrokerOrderNotFoundException("No exposure found for resolved scope: " + resolvedMutationScope);
        }

        return new ResolvedScopeData(foundPair, foundSide, aggregate);
    }

    private List<OrderSnapshot> readOrders(com.hope.trading.broker_service.credential.domain.CredentialMaterial c, String clientId) {
        List<OrderSnapshot> result = new ArrayList<>();
        collect(client.privatePost("/0/private/OpenOrders", Map.of(), c).path("open"), clientId, result);
        collect(client.privatePost("/0/private/ClosedOrders", Map.of(), c).path("closed"), clientId, result);
        return List.copyOf(result);
    }

    private void collect(JsonNode orders, String clientId, List<OrderSnapshot> target) {
        orders.fields().forEachRemaining(e -> {
            OrderSnapshot order = mapOrder(e.getKey(), e.getValue(), clock.instant());
            if (clientId == null || clientId.equals(order.clientOrderId())) target.add(order);
        });
    }

    private OrderSnapshot mapOrder(String txid, JsonNode node, java.time.Instant observedAt) {
        String pair = node.path("descr").path("pair").asText();
        String type = node.path("descr").path("type").asText();
        BigDecimal volume = new BigDecimal(node.path("vol").asText("0"));
        BigDecimal filled = new BigDecimal(node.path("vol_exec").asText("0"));
        String statusStr = node.path("status").asText();
        OrderStatus status = parseStatus(statusStr);
        String clientOrderId = node.path("descr").path("cl_ord_id").asText(null);
        List<FillSnapshot> fills = List.of();
        return new OrderSnapshot(txid, clientOrderId, status, volume, filled, fills, observedAt);
    }

    private OrderStatus parseStatus(String status) {
        return switch (status) {
            case "open" -> OrderStatus.OPEN;
            case "closed" -> OrderStatus.FILLED;
            case "canceled" -> OrderStatus.CANCELLED;
            case "expired" -> OrderStatus.REJECTED;
            case "pending" -> OrderStatus.ACKNOWLEDGED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private String clientOrderId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private String safeCode(Exception e) {
        return e.getClass().getSimpleName().replace("Exception", "").toUpperCase(Locale.ROOT);
    }
}