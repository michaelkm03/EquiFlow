package com.equiflow.order.dto;

import com.equiflow.order.model.enums.OrderSide;
import com.equiflow.order.model.enums.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order submission request")
public class OrderRequest {

    @NotBlank(message = "Ticker is required")
    @Size(max = 10, message = "Ticker must be 10 characters or fewer")
    @Schema(description = "Stock ticker symbol", example = "AAPL")
    private String ticker;

    @NotNull(message = "Order side is required")
    @Schema(description = "BUY or SELL", example = "BUY")
    private OrderSide side;

    @NotNull(message = "Order type is required")
    @Schema(description = "MARKET or LIMIT", example = "MARKET")
    private OrderType type;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Schema(description = "Number of shares", example = "10")
    private BigDecimal quantity;

    @Positive(message = "Limit price must be positive")
    @Schema(description = "Limit price (required for LIMIT orders)", example = "150.00")
    private BigDecimal limitPrice;
}
