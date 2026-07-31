package com.hope.trading.trading_core.execution.infrastructure.observability;

import com.hope.trading.trading_core.execution.application.port.ExecutionMetrics;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ExecutionOperationsMetrics implements ExecutionMetrics {
    private final LongAdder created=new LongAdder(), succeeded=new LongAdder(),
            failed=new LongAdder(), cancelled=new LongAdder(), duplicates=new LongAdder(),
            retries=new LongAdder(), recoveries=new LongAdder(), unknown=new LongAdder();
    public void executionCreated(){created.increment();}
    public void executionSucceeded(){succeeded.increment();}
    public void executionFailed(){failed.increment();}
    public void executionCancelled(){cancelled.increment();}
    public void duplicatePrevented(){duplicates.increment();}
    public void retryScheduled(){retries.increment();}
    public void recoveryStarted(){recoveries.increment();}
    public void unknownSubmission(){unknown.increment();}
    public Snapshot snapshot(){return new Snapshot(created.sum(),succeeded.sum(),failed.sum(),
            cancelled.sum(),duplicates.sum(),retries.sum(),recoveries.sum(),unknown.sum());}
    public record Snapshot(long totalExecutions,long successfulExecutions,long failedExecutions,
            long cancelledExecutions,long duplicatesPrevented,long retries,
            long recoveries,long unknownSubmissions){}
}
