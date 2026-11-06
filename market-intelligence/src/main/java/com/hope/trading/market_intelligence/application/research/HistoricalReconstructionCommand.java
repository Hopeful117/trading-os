package com.hope.trading.market_intelligence.application.research;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "research.historical-reconstruction.enabled", havingValue = "true")
public final class HistoricalReconstructionCommand implements ApplicationRunner {
    private final HistoricalReconstructionService service;
    private final ConfigurableApplicationContext context;
    private final Path outputRoot;

    public HistoricalReconstructionCommand(
            HistoricalReconstructionService service,
            ConfigurableApplicationContext context,
            @Value("${research.historical-reconstruction.output-root}") Path outputRoot
    ) {
        this.service = service;
        this.context = context;
        this.outputRoot = outputRoot;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        UUID marketId = UUID.fromString(required(arguments, "market-id"));
        Instant cutoff = Instant.parse(required(arguments, "cutoff"));
        String runId = required(arguments, "run-id");
        String approval = required(arguments, "approval-reference");
        var confirmationValues = arguments.getOptionValues("confirm-historical-reconstruction");
        if (confirmationValues != null && !confirmationValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "--confirm-historical-reconstruction is a flag and must not have a value");
        }
        boolean confirmed = arguments.containsOption("confirm-historical-reconstruction");
        HistoricalReconstructionService.Result result =
                service.run(marketId, cutoff, outputRoot, runId, confirmed, approval);
        int exitCode = "EXPORTED".equals(result.status()) ? 0 : 1;
        SpringApplication.exit(context, () -> exitCode);
        if (exitCode != 0) {
            throw new IllegalStateException("Historical reconstruction did not export an eligible sample: "
                    + result.status());
        }
    }

    private String required(ApplicationArguments arguments, String name) {
        var values = arguments.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("required option: --" + name + "=<value>");
        }
        return values.getFirst();
    }
}
