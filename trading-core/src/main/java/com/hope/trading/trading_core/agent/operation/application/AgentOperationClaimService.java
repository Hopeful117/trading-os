package com.hope.trading.trading_core.agent.operation.application;
import com.hope.trading.trading_core.agent.operation.repository.AgentOperationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class AgentOperationClaimService {
    private final AgentOperationRequestRepository requests;
    @Transactional
    public boolean claim(UUID id, UUID agentId, Instant now) { return requests.claim(id, agentId, now) == 1; }
}
