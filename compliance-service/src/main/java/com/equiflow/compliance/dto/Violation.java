package com.equiflow.compliance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A compliance violation detail")
public class Violation {

    @Schema(description = "Violation rule code", example = "WASH_SALE")
    private String code;

    @Schema(description = "Human-readable description", example = "Wash-sale rule violation detected")
    private String description;

    @Schema(description = "Severity level", example = "HARD_BLOCK")
    private String severity; // HARD_BLOCK, WARNING
}
