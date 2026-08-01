package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.model.ValuationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValuationSnapshotRepository extends JpaRepository<ValuationSnapshot, Long> {
}
