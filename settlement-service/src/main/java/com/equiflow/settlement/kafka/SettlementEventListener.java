package com.equiflow.settlement.kafka;

import com.equiflow.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final SettlementService settlementService;

    @KafkaListener(topics = "equiflow.order.filled", groupId = "settlement-service")
    public void onOrderFilled(Map<String, Object> event) {
        try {
            String orderId = (String) event.get("orderId");
            String userId = (String) event.get("userId");
            String ticker = (String) event.get("ticker");
            String side = (String) event.get("side");
            Object filledQtyObj = event.get("filledQty");
            Object filledPriceObj = event.get("filledPrice");

            if (orderId == null || filledQtyObj == null || filledPriceObj == null) {
                log.warn("Received incomplete order.filled event: {}", event);
                return;
            }

            BigDecimal quantity = new BigDecimal(filledQtyObj.toString());
            BigDecimal fillPrice = new BigDecimal(filledPriceObj.toString());

            settlementService.createSettlement(
                    UUID.fromString(orderId),
                    UUID.fromString(userId),
                    ticker,
                    side,
                    quantity,
                    fillPrice
            );

            log.info("Settlement record created from order.filled event for order {}", orderId);
        } catch (Exception e) {
            log.error("Error processing order.filled event: {}", e.getMessage(), e);
        }
    }
}
