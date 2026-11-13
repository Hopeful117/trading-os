package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataPassiveScanAttemptRepository extends JpaRepository<JpaPassiveScanAttemptEntity, UUID> {
}
