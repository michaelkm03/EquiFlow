package com.equiflow.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_checks", indexes = {
    @Index(name = "idx_compliance_order_id", columnList = "order_id"),
    @Index(name = "idx_compliance_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "result", nullable = false, length = 10)
    private String result; // APPROVED, REJECTED

    @Column(name = "violations", columnDefinition = "TEXT")
    private String violations; // JSON array of violation descriptions

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @PrePersist
    protected void onCreate() {
        checkedAt = Instant.now();
    }
}
