package com.equiflow.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account balance and position summary")
public class AccountResponse {

    @Schema(description = "User ID")
    private UUID userId;

    @Schema(description = "Total cash balance")
    private BigDecimal cashBalance;

    @Schema(description = "Cash currently on hold for pending orders")
    private BigDecimal cashOnHold;

    @Schema(description = "Available cash (balance minus holds)")
    private BigDecimal availableCash;

    @Schema(description = "Last update time")
    private Instant updatedAt;
}
