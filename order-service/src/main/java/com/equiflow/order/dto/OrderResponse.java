package com.equiflow.order.dto;

import com.equiflow.order.model.enums.OrderSide;
import com.equiflow.order.model.enums.OrderStatus;
import com.equiflow.order.model.enums.OrderType;
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
@Schema(description = "Order details response")
public class OrderResponse {

    @Schema(description = "Order ID")
    private UUID id;

    @Schema(description = "User ID")
    private UUID userId;

    @Schema(description = "Ticker symbol")
    private String ticker;

    @Schema(description = "Order side")
    private OrderSide side;

    @Schema(description = "Order type")
    private OrderType type;

    @Schema(description = "Requested quantity")
    private BigDecimal quantity;

    @Schema(description = "Limit price (null for market orders)")
    private BigDecimal limitPrice;

    @Schema(description = "Average fill price")
    private BigDecimal filledPrice;

    @Schema(description = "Filled quantity")
    private BigDecimal filledQty;

    @Schema(description = "Order status")
    private OrderStatus status;

    @Schema(description = "Associated saga ID")
    private UUID sagaId;

    @Schema(description = "Rejection reason (if rejected)")
    private String rejectionReason;

    @Schema(description = "Order creation time")
    private Instant createdAt;

    @Schema(description = "Order expiry time")
    private Instant expiresAt;

    @Schema(description = "Last update time")
    private Instant updatedAt;
}
