package com.equiflow.settlement.kafka;

import com.equiflow.settlement.model.Settlement;
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
public class SettlementEventPublisher {

    private static final String TOPIC_SETTLED = "equiflow.settlement.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSettled(Settlement settlement) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "SETTLEMENT_COMPLETED");
        event.put("settlementId", settlement.getId().toString());
        event.put("orderId", settlement.getOrderId().toString());
        event.put("userId", settlement.getUserId().toString());
        event.put("ticker", settlement.getTicker());
        event.put("side", settlement.getSide());
        event.put("quantity", settlement.getQuantity());
        event.put("totalAmount", settlement.getTotalAmount());
        event.put("settlementDate", settlement.getSettlementDate().toString());
        event.put("timestamp", Instant.now().toString());
        event.put("service", "settlement-service");

        kafkaTemplate.send(TOPIC_SETTLED, settlement.getOrderId().toString(), event);
        log.info("Published SETTLEMENT_COMPLETED for order {}", settlement.getOrderId());
    }
}
