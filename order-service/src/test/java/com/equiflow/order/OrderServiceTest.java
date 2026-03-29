package com.equiflow.order;

import com.equiflow.order.config.MarketHoursValidator;
import com.equiflow.order.dto.OrderRequest;
import com.equiflow.order.dto.OrderResponse;
import com.equiflow.order.kafka.OrderEventPublisher;
import com.equiflow.order.matching.MatchingEngine;
import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderSide;
import com.equiflow.order.model.enums.OrderStatus;
import com.equiflow.order.model.enums.OrderType;
import com.equiflow.order.repository.OrderRepository;
import com.equiflow.order.service.OrderService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class OrderServiceTest {

    private static final UUID USER_ID  = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");

    private OrderRepository orderRepository;
    private MatchingEngine matchingEngine;
    private OrderEventPublisher eventPublisher;
    private MarketHoursValidator marketHoursValidator;
    private OrderService orderService;

    @BeforeMethod
    public void setUp() {
        orderRepository = mock(OrderRepository.class);
        matchingEngine = mock(MatchingEngine.class);
        eventPublisher = mock(OrderEventPublisher.class);
        marketHoursValidator = mock(MarketHoursValidator.class);
        orderService = new OrderService(orderRepository, matchingEngine, eventPublisher, marketHoursValidator);
    }

    // -------------------------------------------------------------------------
    // submitOrder
    // -------------------------------------------------------------------------

    @Test(description = "Market order during market hours is persisted, matched, and FILLED event published")
    public void testMarketOrderSubmission() {
        UUID userId = UUID.randomUUID();
        when(marketHoursValidator.isMarketOpen()).thenReturn(true);

        Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .ticker("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .quantity(new BigDecimal("10"))
                .status(OrderStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Order filledOrder = Order.builder()
                .id(savedOrder.getId())
                .userId(userId)
                .ticker("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .quantity(new BigDecimal("10"))
                .filledQty(new BigDecimal("10"))
                .filledPrice(new BigDecimal("150.00"))
                .status(OrderStatus.FILLED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(matchingEngine.executeMarketOrder(any(Order.class))).thenReturn(filledOrder);

        OrderRequest request = new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("10"), null, null);

        OrderResponse response = orderService.submitOrder(request, userId);

        assertNotNull(response);
        assertEquals(response.getTicker(), "AAPL");
        assertEquals(response.getStatus(), OrderStatus.FILLED);
        // Order must be persisted and placed event published before matching
        verify(orderRepository).save(any());
        verify(eventPublisher).publishOrderPlaced(any());
        verify(matchingEngine).executeMarketOrder(any());
    }

    @Test(description = "Market closed — exception thrown, nothing persisted, no event published")
    public void testMarketHoursRejection() {
        when(marketHoursValidator.isMarketOpen()).thenReturn(false);

        OrderRequest request = new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("10"), null, null);

        try {
            orderService.submitOrder(request, UUID.randomUUID());
            fail("Expected IllegalStateException for closed market");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Market is closed"), "Wrong exception message: " + ex.getMessage());
        }

        // Rejection must be a complete no-op — nothing persisted, no event emitted
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderPlaced(any());
    }

    @Test(description = "LIMIT order missing limitPrice — exception thrown, nothing persisted, no event published")
    public void testLimitOrderWithoutPriceRejected() {
        when(marketHoursValidator.isMarketOpen()).thenReturn(true);

        OrderRequest request = new OrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10"), null, null);

        try {
            orderService.submitOrder(request, UUID.randomUUID());
            fail("Expected IllegalArgumentException for missing limit price");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Limit price"), "Wrong exception message: " + ex.getMessage());
        }

        // Validation fires before any persistence — nothing should be saved or published
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderPlaced(any());
    }

    // -------------------------------------------------------------------------
    // EQ-113b — systemCancelOrder: cancellable states
    // Each test verifies status → CANCELLED, save called, event published
    // -------------------------------------------------------------------------

    @Test(description = "PENDING order is cancelled — save called, event published")
    public void systemCancel_pendingOrder_cancels() {
        // PENDING: order submitted but not yet compliance-checked
        stubRepo(OrderStatus.PENDING);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.CANCELLED);
        verify(orderRepository).save(any());
        verify(eventPublisher).publishOrderCancelled(any());
    }

    @Test(description = "COMPLIANCE_CHECK order is cancelled — save called, event published")
    public void systemCancel_complianceCheckOrder_cancels() {
        // COMPLIANCE_CHECK: mid-saga step 1 — not yet matched, safe to cancel
        stubRepo(OrderStatus.COMPLIANCE_CHECK);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.CANCELLED);
        verify(orderRepository).save(any());
        verify(eventPublisher).publishOrderCancelled(any());
    }

    @Test(description = "OPEN order is cancelled — save called, event published")
    public void systemCancel_openOrder_cancels() {
        // OPEN: resting in order book, not yet matched
        stubRepo(OrderStatus.OPEN);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.CANCELLED);
        verify(orderRepository).save(any());
        verify(eventPublisher).publishOrderCancelled(any());
    }

    @Test(description = "PENDING_TRIGGER order is cancelled — save called, event published")
    public void systemCancel_pendingTriggerOrder_cancels() {
        // PENDING_TRIGGER: stop-loss set but trigger price not yet hit
        stubRepo(OrderStatus.PENDING_TRIGGER);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.CANCELLED);
        verify(orderRepository).save(any());
        verify(eventPublisher).publishOrderCancelled(any());
    }

    @Test(description = "TRIGGERED order is cancelled — save called, event published")
    public void systemCancel_triggeredOrder_cancels() {
        // TRIGGERED: stop-loss fired but order not yet re-submitted to matching
        stubRepo(OrderStatus.TRIGGERED);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.CANCELLED);
        verify(orderRepository).save(any());
        verify(eventPublisher).publishOrderCancelled(any());
    }

    // -------------------------------------------------------------------------
    // EQ-113b — systemCancelOrder: already-terminal no-ops
    // Compensation may be called twice; safe with no DB write or event
    // -------------------------------------------------------------------------

    @Test(description = "CANCELLED order is a no-op — no save, no event")
    public void systemCancel_alreadyCancelled_isNoOp() {
        stubRepo(OrderStatus.CANCELLED);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    @Test(description = "REJECTED order is a no-op — no save, no event")
    public void systemCancel_rejectedOrder_isNoOp() {
        stubRepo(OrderStatus.REJECTED);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.REJECTED);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    @Test(description = "FAILED order is a no-op — no save, no event")
    public void systemCancel_failedOrder_isNoOp() {
        stubRepo(OrderStatus.FAILED);
        OrderResponse response = orderService.systemCancelOrder(ORDER_ID, USER_ID);
        assertEquals(response.getStatus(), OrderStatus.FAILED);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    // -------------------------------------------------------------------------
    // EQ-113b — systemCancelOrder: money-moved states
    // Matching already ran — exception thrown, no side effects
    // -------------------------------------------------------------------------

    @Test(description = "FILLED order throws ORDER_IN_TERMINAL_STATE — no save, no event")
    public void systemCancel_filledOrder_returns409() {
        // FILLED: matching ran and debited the ledger — cannot reverse without manual reconciliation
        stubRepo(OrderStatus.FILLED);
        try {
            orderService.systemCancelOrder(ORDER_ID, USER_ID);
            fail("Expected IllegalStateException for FILLED order");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("ORDER_IN_TERMINAL_STATE"), "Wrong exception message: " + ex.getMessage());
        }
        // Exception must be thrown before any persistence or event
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    @Test(description = "PARTIALLY_FILLED order throws ORDER_IN_TERMINAL_STATE — no save, no event")
    public void systemCancel_partiallyFilledOrder_returns409() {
        // PARTIALLY_FILLED: partial fill means money partially moved — treated same as FILLED
        stubRepo(OrderStatus.PARTIALLY_FILLED);
        try {
            orderService.systemCancelOrder(ORDER_ID, USER_ID);
            fail("Expected IllegalStateException for PARTIALLY_FILLED order");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("ORDER_IN_TERMINAL_STATE"), "Wrong exception message: " + ex.getMessage());
        }
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCancelled(any());
    }

    // -------------------------------------------------------------------------
    // EQ-113b — systemCancelOrder: negative / guard cases
    // -------------------------------------------------------------------------

    @Test(description = "Unknown orderId throws — repo returns empty Optional",
          expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*Order not found.*")
    public void systemCancel_orderNotFound_throws() {
        // findByIdAndUserId returns empty when the order does not exist for this user
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());
        orderService.systemCancelOrder(ORDER_ID, USER_ID);
    }

    @Test(description = "Wrong userId throws — repo returns empty for mismatched user",
          expectedExceptions = IllegalArgumentException.class)
    public void systemCancel_wrongUser_throws() {
        // Repository filters by both orderId AND userId — wrong userId yields empty Optional
        UUID wrongUserId = UUID.randomUUID();
        when(orderRepository.findByIdAndUserId(ORDER_ID, wrongUserId)).thenReturn(Optional.empty());
        orderService.systemCancelOrder(ORDER_ID, wrongUserId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Stubs repo to return an order with the given status; save() echoes back with CANCELLED set. */
    private void stubRepo(OrderStatus status) {
        Order order = buildOrder(USER_ID, status);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setStatus(OrderStatus.CANCELLED);
            return saved;
        });
    }

    private Order buildOrder(UUID userId, OrderStatus status) {
        return Order.builder()
                .id(ORDER_ID)
                .userId(userId)
                .ticker("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("10"))
                .limitPrice(new BigDecimal("150.00"))
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
