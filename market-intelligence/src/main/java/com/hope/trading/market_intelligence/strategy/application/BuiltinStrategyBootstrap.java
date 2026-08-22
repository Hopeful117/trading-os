package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bootstraps the builtin strategy catalogue into persistent storage
 * (ADR-037).
 *
 * <p>Responsibilities are strictly split:</p>
 * <ul>
 *   <li>{@link BuiltinStrategies} — software-owned catalogue of known
 *       strategies and their INITIAL definitions;</li>
 *   <li>{@code strategy_definitions} table — persisted runtime governance
 *       state and the runtime source of truth for the pipeline.</li>
 * </ul>
 *
 * <p>Bootstrap policy:</p>
 * <ul>
 *   <li>missing definition → inserted with its builtin initial state;</li>
 *   <li>existing definition → LEFT UNTOUCHED: persisted governance always
 *       survives restarts and is never reset to builtin initial values
 *       (restart invariant, ADR-037);</li>
 *   <li>new builtin version → new persisted definition with initial
 *       governance; no automatic inheritance of validation/activation from a
 *       previous version.</li>
 * </ul>
 *
 * <p>Bootstrap is idempotent: two consecutive runs produce the same final
 * state. It never constitutes implicit human approval of a strategy. A
 * bootstrap failure fails startup visibly; there is deliberately no silent
 * fallback to in-memory builtins, which would recreate a second source of
 * truth.</p>
 */
@Component
public class BuiltinStrategyBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BuiltinStrategyBootstrap.class);

    private final BuiltinStrategies builtins;
    private final StrategyDefinitionRepository repository;

    public BuiltinStrategyBootstrap(
            BuiltinStrategies builtins, StrategyDefinitionRepository repository) {
        this.builtins = builtins;
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<StrategyDefinition> catalog = builtins.all();
        int inserted = 0;
        for (StrategyDefinition definition : catalog) {
            if (repository.find(definition.strategyId(), definition.version()).isEmpty()) {
                repository.save(definition);
                inserted++;
                log.info("Bootstrapped strategy {}v{} ({})", definition.name(),
                        definition.version(), definition.operationalStatus());
            }
        }
        log.debug("Strategy bootstrap complete: {} catalog entries, {} inserted",
                catalog.size(), inserted);
    }
}
