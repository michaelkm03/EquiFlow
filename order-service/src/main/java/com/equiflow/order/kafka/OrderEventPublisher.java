package com.equiflow.order.kafka;

import com.equiflow.order.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final String TOPIC_PLACED = "equiflow.order.placed";
    private static final String TOPIC_FILLED = "equiflow.order.filled";
    private static final String TOPIC_CANCELLED = "equiflow.order.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrderPlaced(Order order) {
        Map<String, Object> event = buildEvent("ORDER_PLACED", order);
        kafkaTemplate.send(TOPIC_PLACED, order.getId().toString(), event);
        log.info("Published ORDER_PLACED event for order: {}", order.getId());
    }

    public void publishOrderFilled(Order order) {
        Map<String, Object> event = buildEvent("ORDER_FILLED", order);
        kafkaTemplate.send(TOPIC_FILLED, order.getId().toString(), event);
        log.info("Published ORDER_FILLED event for order: {}", order.getId());
    }

    public void publishOrderCancelled(Order order) {
        Map<String, Object> event = buildEvent("ORDER_CANCELLED", order);
        kafkaTemplate.send(TOPIC_CANCELLED, order.getId().toString(), event);
        log.info("Published ORDER_CANCELLED event for order: {}", order.getId());
    }

    private Map<String, Object> buildEvent(String eventType, Order order) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("orderId", order.getId().toString());
        event.put("userId", order.getUserId().toString());
        event.put("ticker", order.getTicker());
        event.put("side", order.getSide().name());
        event.put("type", order.getType().name());
        event.put("quantity", order.getQuantity());
        event.put("limitPrice", order.getLimitPrice());
        event.put("filledPrice", order.getFilledPrice());
        event.put("filledQty", order.getFilledQty());
        event.put("status", order.getStatus().name());
        event.put("sagaId", order.getSagaId() != null ? order.getSagaId().toString() : null);
        event.put("timestamp", Instant.now().toString());
        event.put("service", "order-service");
        return event;
    }
}
