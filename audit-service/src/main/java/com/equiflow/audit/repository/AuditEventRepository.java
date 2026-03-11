package com.equiflow.audit.repository;

import com.equiflow.audit.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit repository - read and insert only. No update or delete methods exposed.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Page<AuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    Page<AuditEvent> findBySourceServiceOrderByCreatedAtDesc(String sourceService, Pageable pageable);

    @Query("SELECT e FROM AuditEvent e WHERE e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
    Page<AuditEvent> findByTimeRange(Instant from, Instant to, Pageable pageable);

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
