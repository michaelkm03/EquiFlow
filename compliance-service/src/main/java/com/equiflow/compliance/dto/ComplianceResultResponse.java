package com.equiflow.compliance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Compliance check result for a specific order")
public class ComplianceResultResponse {

    @Schema(description = "Order UUID")
    private UUID orderId;

    @Schema(description = "Overall result", example = "REJECTED")
    private String result;

    @Schema(description = "List of violations (empty if approved)")
    private List<Violation> violations;

    @Schema(description = "When the check was performed")
    private Instant checkedAt;
}
