package com.equiflow.settlement.repository;

import com.equiflow.settlement.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findByStatus(String status);
    List<Settlement> findByStatusAndSettlementDateLessThanEqual(String status, LocalDate date);
    Optional<Settlement> findByOrderId(UUID orderId);
    List<Settlement> findByUserId(UUID userId);
}
