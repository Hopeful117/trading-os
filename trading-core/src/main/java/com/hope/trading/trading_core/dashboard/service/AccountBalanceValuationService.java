package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AccountBalanceValuationService {
    private final MarketValuationPort marketValuation;
    private final Clock clock;

    public AccountBalanceValuationService(MarketValuationPort marketValuation, Clock clock) {
        this.marketValuation = marketValuation;
        this.clock = clock;
    }

    public AccountValuationResult value(String reportingCurrency, Map<String, BigDecimal> balances) {
        String currency = normalize(reportingCurrency);
        Map<String, BigDecimal> normalized = normalizeBalances(balances);
        List<MarketValuationPort.Asset> assets = normalized.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().signum() != 0)
                .map(entry -> new MarketValuationPort.Asset(entry.getKey(), entry.getKey()))
                .toList();

        try {
            MarketValuationPort.Snapshot snapshot = marketValuation.value(
                    currency, clock.instant(), List.of(), assets);
            Map<String, MarketValuationPort.Fact> facts = snapshot.facts().stream()
                    .filter(fact -> "ASSET".equals(fact.type()))
                    .collect(java.util.stream.Collectors.toMap(
                            fact -> normalize(fact.asset()), fact -> fact, (first, ignored) -> first));
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> balance : normalized.entrySet()) {
                if (balance.getValue() == null) {
                    return new AccountValuationResult(null, "INCOMPLETE", snapshot.valuationTimestamp(),
                            snapshot.policyVersion(), "Montant indisponible pour " + balance.getKey());
                }
                if (balance.getValue().signum() == 0) continue;
                MarketValuationPort.Fact fact = facts.get(balance.getKey());
                if (fact == null || !"AVAILABLE".equals(fact.status()) || fact.value() == null) {
                    return new AccountValuationResult(null, "INCOMPLETE", snapshot.valuationTimestamp(),
                            snapshot.policyVersion(), "Valuation indisponible pour " + balance.getKey());
                }
                total = total.add(balance.getValue().multiply(fact.value()));
            }
            if (!snapshot.complete()) {
                return new AccountValuationResult(null, "INCOMPLETE", snapshot.valuationTimestamp(),
                        snapshot.policyVersion(), "Valuation multi-actifs incomplète");
            }
            return new AccountValuationResult(total, "COMPLETE", snapshot.valuationTimestamp(),
                    snapshot.policyVersion(), null);
        } catch (RuntimeException exception) {
            return AccountValuationResult.unavailable("Market Data Service indisponible");
        }
    }

    private Map<String, BigDecimal> normalizeBalances(Map<String, BigDecimal> balances) {
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        if (balances == null) return normalized;
        balances.forEach((asset, amount) -> {
            if (asset != null && !asset.isBlank()) {
                String key = normalize(asset);
                if (!normalized.containsKey(key)) {
                    normalized.put(key, amount);
                } else {
                    normalized.put(key, addNullable(normalized.get(key), amount));
                }
            }
        });
        return normalized;
    }

    private BigDecimal addNullable(BigDecimal first, BigDecimal second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.add(second);
    }

    private String normalize(String currency) {
        if (currency == null) return "";
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("BTC")) return "XBT";
        if (normalized.length() == 4
                && (normalized.startsWith("X") || normalized.startsWith("Z"))) {
            return normalized.substring(1);
        }
        return normalized;
    }
}
