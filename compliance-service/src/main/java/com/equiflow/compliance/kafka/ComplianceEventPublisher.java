package com.equiflow.compliance.kafka;

import com.equiflow.compliance.dto.Violation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceEventPublisher {

    private static final String TOPIC_APPROVED = "equiflow.compliance.approved";
    private static final String TOPIC_REJECTED = "equiflow.compliance.rejected";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishApproved(UUID orderId, UUID userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "COMPLIANCE_APPROVED");
        event.put("orderId", orderId.toString());
        event.put("userId", userId.toString());
        event.put("timestamp", Instant.now().toString());
        event.put("service", "compliance-service");

        kafkaTemplate.send(TOPIC_APPROVED, orderId.toString(), event);
        log.info("Published COMPLIANCE_APPROVED for order {}", orderId);
    }

    public void publishRejected(UUID orderId, UUID userId, List<Violation> violations) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "COMPLIANCE_REJECTED");
        event.put("orderId", orderId.toString());
        event.put("userId", userId.toString());
        event.put("violations", violations);
        event.put("timestamp", Instant.now().toString());
        event.put("service", "compliance-service");

        kafkaTemplate.send(TOPIC_REJECTED, orderId.toString(), event);
        log.info("Published COMPLIANCE_REJECTED for order {}", orderId);
    }
}
