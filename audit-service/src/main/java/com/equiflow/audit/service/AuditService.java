package com.equiflow.audit.service;

import com.equiflow.audit.model.AuditEvent;
import com.equiflow.audit.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AuditEvent logEvent(String eventType, String sourceService,
                                UUID orderId, UUID userId, Map<String, Object> payload,
                                String topic, Long offset) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = payload != null ? payload.toString() : "{}";
        }

        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .sourceService(sourceService)
                .orderId(orderId)
                .userId(userId)
                .payload(payloadJson)
                .kafkaTopic(topic)
                .kafkaOffset(offset)
                .createdAt(Instant.now())
                .build();

        AuditEvent saved = auditEventRepository.save(event);
        log.debug("Audit event logged: {} from {}", eventType, sourceService);
        return saved;
    }

    public List<AuditEvent> getEventsByOrder(UUID orderId) {
        return auditEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    public Page<AuditEvent> getEventsByUser(UUID userId, int page, int size) {
        return auditEventRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    public Page<AuditEvent> getEventsByType(String eventType, int page, int size) {
        return auditEventRepository.findByEventTypeOrderByCreatedAtDesc(
                eventType, PageRequest.of(page, size));
    }

    public Page<AuditEvent> getAllEvents(int page, int size) {
        return auditEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<AuditEvent> getEventsByTimeRange(Instant from, Instant to, int page, int size) {
        return auditEventRepository.findByTimeRange(from, to, PageRequest.of(page, size));
    }
}
