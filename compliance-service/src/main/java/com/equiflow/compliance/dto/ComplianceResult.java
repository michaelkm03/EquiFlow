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
@Schema(description = "Compliance check result")
public class ComplianceResult {

    @Schema(description = "Check ID")
    private UUID checkId;

    @Schema(description = "Order ID")
    private UUID orderId;

    @Schema(description = "Whether the order is approved (true) or rejected (false)")
    private boolean approved;

    @Schema(description = "List of violations (empty if approved)")
    private List<Violation> violations;

    @Schema(description = "Summary message")
    private String message;

    @Schema(description = "Check timestamp")
    private Instant checkedAt;
}
