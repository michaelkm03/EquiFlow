package com.equiflow.saga.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Saga saga;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "step_name", nullable = false, length = 50)
    private String stepName;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, COMPLETED, FAILED, COMPENSATED

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "executed_at")
    private Instant executedAt;
}
