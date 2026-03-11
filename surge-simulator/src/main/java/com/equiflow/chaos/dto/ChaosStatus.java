package com.equiflow.chaos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current chaos session status")
public class ChaosStatus {

    @Schema(description = "Session ID")
    private UUID sessionId;

    @Schema(description = "Whether chaos is currently active")
    private boolean active;

    @Schema(description = "Current chaos mode")
    private String mode;

    @Schema(description = "Configured latency in milliseconds")
    private int latencyMs;

    @Schema(description = "DB failure rate (0-100%)")
    private int failureRatePercent;

    @Schema(description = "Session start time")
    private Instant startedAt;

    @Schema(description = "Who triggered the session")
    private String triggeredBy;
}
