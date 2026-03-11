package com.equiflow.market.repository;

import com.equiflow.market.model.ScenarioEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScenarioEventRepository extends JpaRepository<ScenarioEvent, UUID> {
    List<ScenarioEvent> findByScenarioNameOrderByCreatedAtDesc(String scenarioName);
}
