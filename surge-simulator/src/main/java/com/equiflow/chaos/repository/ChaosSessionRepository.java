package com.equiflow.chaos.repository;

import com.equiflow.chaos.model.ChaosSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChaosSessionRepository extends JpaRepository<ChaosSession, UUID> {
    List<ChaosSession> findByStatusOrderByStartedAtDesc(String status);
}
