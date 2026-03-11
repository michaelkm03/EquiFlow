package com.equiflow.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Compliance check request for an order")
public class ComplianceRequest {

    @NotNull
    @Schema(description = "Order ID to check")
    private UUID orderId;

    @NotNull
    @Schema(description = "User placing the order")
    private UUID userId;

    @NotBlank
    @Schema(description = "Ticker symbol", example = "AAPL")
    private String ticker;

    @NotNull
    @Schema(description = "Order side: BUY or SELL", example = "BUY")
    private String side;

    @NotNull
    @Positive
    @Schema(description = "Order quantity", example = "10")
    private BigDecimal quantity;

    @Positive
    @Schema(description = "Estimated order value", example = "1500.00")
    private BigDecimal estimatedValue;

    @Positive
    @Schema(description = "Current available cash balance")
    private BigDecimal availableCash;
}
