package com.equiflow.order.service;

import com.equiflow.order.config.MarketHoursValidator;
import com.equiflow.order.dto.OrderRequest;
import com.equiflow.order.dto.OrderResponse;
import com.equiflow.order.kafka.OrderEventPublisher;
import com.equiflow.order.matching.MatchingEngine;
import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderStatus;
import com.equiflow.order.model.enums.OrderType;
import com.equiflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final OrderEventPublisher eventPublisher;
    private final MarketHoursValidator marketHoursValidator;

    @Transactional
    public OrderResponse submitOrder(OrderRequest request, UUID userId) {
        log.info("Submitting {} {} order for user {} on {}",
                request.getType(), request.getSide(), userId, request.getTicker());

        if (!marketHoursValidator.isMarketOpen()) {
            log.warn("Order rejected: market is closed");
            throw new IllegalStateException("Market is closed. NYSE trading hours are 9:30 AM - 4:00 PM ET, Monday-Friday.");
        }

        if (request.getType() == OrderType.LIMIT && request.getLimitPrice() == null) {
            throw new IllegalArgumentException("Limit price is required for LIMIT orders");
        }

        Order order = Order.builder()
                .userId(userId)
                .ticker(request.getTicker().toUpperCase())
                .side(request.getSide())
                .type(request.getType())
                .quantity(request.getQuantity())
                .limitPrice(request.getLimitPrice())
                .triggerPrice(request.getTriggerPrice())
                .status(OrderStatus.PENDING)
                .expiresAt(request.getType() == OrderType.LIMIT
                        ? Instant.now().plus(1, ChronoUnit.DAYS)
                        : null)
                .build();

        order = orderRepository.save(order);
        eventPublisher.publishOrderPlaced(order);

        // Execute immediately
        if (request.getType() == OrderType.STOP_LOSS) {
            return toResponse(order);
        } else if (request.getType() == OrderType.MARKET) {
            order = matchingEngine.executeMarketOrder(order);
        } else {
            order = matchingEngine.executeLimitOrder(order);
        }

        return toResponse(order);
    }

    @Transactional
    public OrderResponse triggerMatch(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getType() == OrderType.MARKET) {
            order = matchingEngine.executeMarketOrder(order);
        } else {
            order = matchingEngine.executeLimitOrder(order);
        }

        return toResponse(order);
    }

    public OrderResponse getOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        return toResponse(order);
    }

    public Page<OrderResponse> listOrders(LocalDate from, LocalDate to, String status, String ticker, UUID userId, Pageable pageable) {
        Specification<Order> spec = Specification.where((root, query, cb) -> cb.equal(root.get("userId"), userId));

        if (from != null) {
            Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
        }
        if (to != null) {
            Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), toInstant));
        }
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), orderStatus));
        }
        if (ticker != null && !ticker.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ticker"), ticker.toUpperCase()));
        }

        return orderRepository.findAll(spec, pageable == null ? Pageable.unpaged() : pageable).map(this::toResponse);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.OPEN && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order);
        log.info("Order {} cancelled by user {}", orderId, userId);
        return toResponse(order);
    }

    public Map<String, Object> getOrderBook(String ticker) {
        return matchingEngine.getBookSnapshot(ticker);
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .ticker(order.getTicker())
                .side(order.getSide())
                .type(order.getType())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .filledPrice(order.getFilledPrice())
                .filledQty(order.getFilledQty())
                .status(order.getStatus())
                .sagaId(order.getSagaId())
                .rejectionReason(order.getRejectionReason())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
