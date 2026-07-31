package com.hope.trading.trading_core.execution;

import org.junit.jupiter.api.Test;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

class ExecutionArchitectureTest {
    @Test void domainHasNoFrameworkBrokerOrInfrastructureDependency() throws Exception{
        try(var files=Files.walk(Path.of("src/main/java/com/hope/trading/trading_core/execution/domain"))){
            String source=files.filter(p->p.toString().endsWith(".java"))
                    .map(this::read).reduce("",String::concat);
            assertThat(source).doesNotContain("org.springframework","jakarta.persistence",
                    ".execution.infrastructure.","feign.","broker_service");
        }
    }
    private String read(Path path){
        try{return Files.readString(path);}catch(Exception e){throw new IllegalStateException(e);}
    }
}
