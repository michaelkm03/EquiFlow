package com.equiflow.chaos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chaos injection configuration")
public class ChaosRequest {

    @NotBlank
    @Schema(description = "Chaos mode: NETWORK_LATENCY, DB_FAILURE, or BOTH", example = "NETWORK_LATENCY")
    private String mode;

    @Min(0)
    @Max(30000)
    @Schema(description = "Latency in milliseconds (for NETWORK_LATENCY mode)", example = "2000")
    private int latencyMs;

    @Min(0)
    @Max(100)
    @Schema(description = "DB failure rate as percentage 0-100 (for DB_FAILURE mode)", example = "30")
    private int failureRatePercent;

    @Schema(description = "Who triggered this session", example = "regulator1")
    private String triggeredBy;
}
