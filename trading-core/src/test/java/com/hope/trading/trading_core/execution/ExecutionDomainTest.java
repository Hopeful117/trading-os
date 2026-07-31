package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.service.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static com.hope.trading.trading_core.execution.ExecutionTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ExecutionDomainTest {
    @Test void terminalIntentCannotExecuteAgain(){
        var intent=intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS,NOW);
        intent.transition(ExecutionStatus.COMPLETED,NOW);
        assertThatThrownBy(()->intent.transition(ExecutionStatus.VALIDATED,NOW))
                .isInstanceOf(InvalidExecutionStateException.class);
    }
    @Test void onlyOneAttemptCanBeActive(){
        var intent=intent(ExecutionStatus.VALIDATED);
        intent.activateAttempt(new ExecutionAttemptId(uuid(20)),NOW);
        assertThatThrownBy(()->intent.activateAttempt(new ExecutionAttemptId(uuid(21)),NOW))
                .isInstanceOf(InvalidExecutionStateException.class);
    }
    @Test void unknownOutcomeRequiresReconciliation(){
        var intent=intent(ExecutionStatus.VALIDATED);
        intent.transition(ExecutionStatus.SUBMISSION_IN_PROGRESS,NOW);
        intent.transition(ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN,NOW);
        assertThat(new RecoveryStrategyService().determine(intent))
                .isEqualTo(RecoveryStrategyService.RecoveryStrategy.RECONCILE);
        assertThatThrownBy(()->intent.transition(ExecutionStatus.VALIDATED,Instant.now()))
                .isInstanceOf(InvalidExecutionStateException.class);
    }
}
