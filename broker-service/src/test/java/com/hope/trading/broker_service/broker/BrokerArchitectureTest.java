package com.hope.trading.broker_service.broker;

import java.nio.file.*;import java.util.stream.Stream;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;

class BrokerArchitectureTest {
    @Test void brokerDomainDoesNotDependOnSpringPersistenceOrKraken() throws Exception {Path root=Path.of("src/main/java/com/hope/trading/broker_service/broker/domain");try(Stream<Path> files=Files.walk(root)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){String source=Files.readString(file);assertThat(source).doesNotContain("org.springframework","jakarta.persistence",".kraken.","com.fasterxml.jackson");}}}
}
