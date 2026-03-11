package com.equiflow.audit.kafka;

import com.equiflow.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to ALL equiflow.* topics and logs every event to the audit trail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditService auditService;

    @KafkaListener(
        topicPattern = "equiflow\\..*",
        groupId = "audit-service",
        containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void onAnyEvent(ConsumerRecord<String, Map<String, Object>> record) {
        try {
            Map<String, Object> payload = record.value();
            if (payload == null) {
                log.debug("Received null payload from topic {}", record.topic());
                return;
            }

            String eventType = (String) payload.getOrDefault("eventType", "UNKNOWN");
            String sourceService = (String) payload.getOrDefault("service", "unknown");

            UUID orderId = null;
            String orderIdStr = (String) payload.get("orderId");
            if (orderIdStr != null) {
                try { orderId = UUID.fromString(orderIdStr); } catch (Exception ignored) {}
            }

            UUID userId = null;
            String userIdStr = (String) payload.get("userId");
            if (userIdStr != null) {
                try { userId = UUID.fromString(userIdStr); } catch (Exception ignored) {}
            }

            auditService.logEvent(
                    eventType, sourceService, orderId, userId,
                    payload, record.topic(), record.offset()
            );

        } catch (Exception e) {
            log.error("Error processing audit event from topic {}: {}", record.topic(), e.getMessage(), e);
        }
    }
}
