package com.equiflow.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to debit funds from account")
public class DebitRequest {

    @NotNull
    @Schema(description = "User ID")
    private UUID userId;

    @NotNull
    @Schema(description = "Order ID")
    private UUID orderId;

    @NotNull
    @Positive
    @Schema(description = "Amount to debit", example = "1500.00")
    private BigDecimal amount;

    @Schema(description = "Ticker (for position tracking)")
    private String ticker;

    @Schema(description = "Quantity of shares (for position tracking)")
    private BigDecimal quantity;

    @Schema(description = "Description")
    private String description;
}
