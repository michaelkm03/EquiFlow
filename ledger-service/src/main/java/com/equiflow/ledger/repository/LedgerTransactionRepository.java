package com.equiflow.ledger.repository;

import com.equiflow.ledger.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    List<LedgerTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<LedgerTransaction> findByOrderId(UUID orderId);
    boolean existsByOrderIdAndType(UUID orderId, String type);
}
