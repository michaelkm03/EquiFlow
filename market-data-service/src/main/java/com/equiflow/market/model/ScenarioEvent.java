package com.equiflow.market.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenario_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scenario_name", nullable = false)
    private String scenarioName;

    @Column(name = "action", nullable = false, length = 20)
    private String action; // STARTED, STOPPED, STEP_APPLIED

    @Column(name = "description")
    private String description;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
