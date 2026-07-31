package com.hope.trading.trading_core.execution.infrastructure.observability;

import com.hope.trading.trading_core.execution.application.port.ExecutionEventPublisher;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.infrastructure.persistence.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
public class PersistentExecutionEventPublisher implements ExecutionEventPublisher {
    private static final Logger log=LoggerFactory.getLogger(PersistentExecutionEventPublisher.class);
    private final JpaExecutionEventRepository repository;
    public PersistentExecutionEventPublisher(JpaExecutionEventRepository repository){this.repository=repository;}
    @Override @Transactional public void publish(List<ExecutionEvent> events){
        for(ExecutionEvent event:events){
            ExecutionEventEntity entity=new ExecutionEventEntity();
            entity.id=UUID.randomUUID(); entity.intentId=event.intentId().value();
            entity.eventType=event.getClass().getSimpleName(); entity.occurredAt=event.occurredAt();
            entity.payload=event.toString(); repository.save(entity);
            log.info("execution_event type={} intentId={} occurredAt={}",
                    entity.eventType,entity.intentId,entity.occurredAt);
        }
    }
}
