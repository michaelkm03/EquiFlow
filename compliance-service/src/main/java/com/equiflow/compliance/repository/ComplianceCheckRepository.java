package com.equiflow.compliance.repository;

import com.equiflow.compliance.model.ComplianceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, UUID> {
    Optional<ComplianceCheck> findByOrderId(UUID orderId);
    List<ComplianceCheck> findByUserId(UUID userId);
    List<ComplianceCheck> findByUserIdAndResult(UUID userId, String result);
}
