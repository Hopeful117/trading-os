package com.hope.trading.trading_core.repository;

import com.hope.trading.trading_core.model.Rules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RulesRepository extends JpaRepository<Rules, UUID> {
    Rules findByName(String name);
}
