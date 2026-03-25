package com.equiflow.saga.kafka;

import com.equiflow.saga.model.Saga;
import com.equiflow.saga.service.SagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventListener {

    private final SagaService sagaService;

    @KafkaListener(topics = "equiflow.order.stop-loss.triggered", groupId = "saga-orchestrator")
    public void onStopLossTriggered(Map<String, Object> event) {
        try {
            String orderIdStr = (String) event.get("orderId");
            String userIdStr = (String) event.get("userId");

            if (orderIdStr == null || userIdStr == null) {
                log.warn("Received stop-loss.triggered event with missing orderId or userId");
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            UUID userId = UUID.fromString(userIdStr);

            log.info("Received stop-loss.triggered event for orderId={}", orderId);

            // Override type to MARKET so OrderSaga executes it through the matching engine
            Map<String, Object> marketEvent = new java.util.HashMap<>(event);
            marketEvent.put("type", "MARKET");

            Saga saga = sagaService.startSaga(orderId, userId);
            sagaService.executeAsync(saga, marketEvent);

        } catch (Exception e) {
            log.error("Error processing stop-loss.triggered event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "equiflow.order.placed", groupId = "saga-orchestrator")
    public void onOrderPlaced(Map<String, Object> event) {
        try {
            String orderIdStr = (String) event.get("orderId");
            String userIdStr = (String) event.get("userId");

            if (orderIdStr == null || userIdStr == null) {
                log.warn("Received order.placed event with missing orderId or userId");
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            UUID userId = UUID.fromString(userIdStr);

            log.info("Received order.placed event for orderId={}", orderId);

            Saga saga = sagaService.startSaga(orderId, userId);
            sagaService.executeAsync(saga, event);

        } catch (Exception e) {
            log.error("Error processing order.placed event: {}", e.getMessage(), e);
        }
    }
}
