package com.hope.trading.trading_core.agent.repository;

import com.hope.trading.trading_core.agent.model.ResearchScope;
import com.hope.trading.trading_core.agent.model.ResearchScopeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchScopeRepository extends JpaRepository<ResearchScope, UUID> {
    Optional<ResearchScope> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    List<ResearchScope> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    List<ResearchScope> findAllByStatusAndExpiresAtIsNotNull(ResearchScopeStatus status);
}
