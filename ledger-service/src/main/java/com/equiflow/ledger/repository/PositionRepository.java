package com.equiflow.ledger.repository;

import com.equiflow.ledger.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {
    List<Position> findByUserId(UUID userId);
    Optional<Position> findByUserIdAndTicker(UUID userId, String ticker);
}
