package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

interface SpringDataObservationRepository extends JpaRepository<JpaObservationEntity, UUID> {
    List<JpaObservationEntity> findByInstrumentIgnoreCase(String instrument);
}

interface SpringDataObservationEvidenceRepository
        extends JpaRepository<JpaObservationEvidenceEntity, UUID> {
}
