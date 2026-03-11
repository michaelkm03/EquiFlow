package com.equiflow.chaos.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chaos_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChaosSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "mode", nullable = false, length = 50)
    private String mode; // NETWORK_LATENCY, DB_FAILURE, BOTH

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "failure_rate")
    private Double failureRate; // 0.0 to 1.0

    @Column(name = "status", nullable = false, length = 10)
    private String status; // ACTIVE, STOPPED

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = Instant.now();
    }
}
