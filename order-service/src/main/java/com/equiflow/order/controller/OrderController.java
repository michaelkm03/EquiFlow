package com.equiflow.order.controller;

import com.equiflow.order.dto.OrderRequest;
import com.equiflow.order.dto.OrderResponse;
import com.equiflow.order.service.OrderService;
import com.equiflow.order.service.StopLossService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.crypto.paddings.ISO7816d4Padding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order submission, management, and order book endpoints")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final StopLossService stopLossService;

    @PostMapping
    @Operation(summary = "Submit a new order", description = "Submit a BUY or SELL order (MARKET or LIMIT type)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid order request"),
        @ApiResponse(responseCode = "403", description = "Market closed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<OrderResponse> submitOrder(
            @Valid @RequestBody OrderRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        OrderResponse response = orderService.submitOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID orderId,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(orderService.getOrder(orderId, userId));
    }
// from, to (ISO 8601 dates), status, ticker, page, size
    @GetMapping
    @Operation(summary = "List orders for authenticated user")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(required = false) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(orderService.listOrders(from, to, status, ticker, userId, pageable));
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Cancel an order")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order cancelled"),
        @ApiResponse(responseCode = "409", description = "Order cannot be cancelled in current state")
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID orderId,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        return ResponseEntity.ok(orderService.cancelOrder(orderId, userId));
    }

    @PostMapping("/{orderId}/match")
    @Operation(summary = "Trigger matching for an order (internal use by saga)")
    public ResponseEntity<OrderResponse> triggerMatch(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.triggerMatch(orderId));
    }

    @PostMapping("/{orderId}/system-cancel")
    @Operation(summary = "Cancel an order on behalf of the system (internal use by saga compensation)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order cancelled or already in a terminal no-op state"),
        @ApiResponse(responseCode = "409", description = "Order is FILLED or PARTIALLY_FILLED — money already moved; manual reconciliation required")
    })
    public ResponseEntity<OrderResponse> systemCancel(
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body) {
        UUID userId = UUID.fromString((String) body.get("userId"));
        return ResponseEntity.ok(orderService.systemCancelOrder(orderId, userId));
    }

    @GetMapping("/book/{ticker}")
    @Operation(summary = "Get order book snapshot for a ticker")
    public ResponseEntity<Map<String, Object>> getOrderBook(@PathVariable String ticker) {
        return ResponseEntity.ok(orderService.getOrderBook(ticker));
    }

    @PostMapping("/internal/stop-loss/evaluate")
    @Operation(summary = "Evaluate stop-loss triggers for a ticker (internal use by market-data-service)")
    public ResponseEntity<Void> evaluateStopLoss(
            @RequestParam String ticker,
            @RequestParam BigDecimal currentPrice) {
        stopLossService.evaluateTriggers(ticker, currentPrice);
        return ResponseEntity.ok().build();
    }

    private UUID extractUserId(Authentication auth) {
        // The credentials field holds the userId string set by JwtAuthFilter
        Object credentials = auth.getCredentials();
        if (credentials instanceof String userId) {
            try {
                return UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                // Fall through to use username as ID lookup placeholder
            }
        }
        // Fallback: derive from username for demo purposes
        return UUID.nameUUIDFromBytes(auth.getName().getBytes());
    }
}
