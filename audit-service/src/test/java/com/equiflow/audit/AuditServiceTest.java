package com.equiflow.audit;

import com.equiflow.audit.model.AuditEvent;
import com.equiflow.audit.repository.AuditEventRepository;
import com.equiflow.audit.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class AuditServiceTest {

    private AuditEventRepository auditEventRepository;
    private AuditService auditService;

    @BeforeMethod
    public void setUp() {
        auditEventRepository = mock(AuditEventRepository.class);
        auditService = new AuditService(auditEventRepository, new ObjectMapper());
    }

    @Test(description = "Audit events are persisted and never modified")
    public void testAuditEventAppendOnly() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AuditEvent saved = AuditEvent.builder()
                .id(UUID.randomUUID())
                .eventType("ORDER_PLACED")
                .orderId(orderId)
                .userId(userId)
                .sourceService("order-service")
                .payload("{\"ticker\":\"AAPL\"}")
                .kafkaTopic("equiflow.order.placed")
                .kafkaOffset(42L)
                .createdAt(Instant.now())
                .build();

        when(auditEventRepository.save(any(AuditEvent.class))).thenReturn(saved);

        AuditEvent result = auditService.logEvent(
                "ORDER_PLACED",
                "order-service",
                orderId,
                userId,
                Map.of("ticker", "AAPL", "side", "BUY"),
                "equiflow.order.placed",
                42L
        );

        assertNotNull(result, "Saved audit event should not be null");
        assertEquals(result.getEventType(), "ORDER_PLACED");
        assertEquals(result.getOrderId(), orderId);
        assertEquals(result.getSourceService(), "order-service");
        assertEquals(result.getKafkaTopic(), "equiflow.order.placed");

        // Verify save was called exactly once — no update or delete
        verify(auditEventRepository, times(1)).save(any(AuditEvent.class));
        verify(auditEventRepository, never()).delete(any());
        verify(auditEventRepository, never()).deleteById(any());
        verify(auditEventRepository, never()).deleteAll();
    }

    @Test(description = "Events can be retrieved by order ID")
    public void testGetEventsByOrder() {
        UUID orderId = UUID.randomUUID();
        List<AuditEvent> events = List.of(
                AuditEvent.builder().id(UUID.randomUUID()).eventType("ORDER_PLACED")
                        .orderId(orderId).sourceService("order-service").createdAt(Instant.now()).build(),
                AuditEvent.builder().id(UUID.randomUUID()).eventType("ORDER_FILLED")
                        .orderId(orderId).sourceService("order-service").createdAt(Instant.now()).build()
        );

        when(auditEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(events);

        List<AuditEvent> result = auditService.getEventsByOrder(orderId);

        assertEquals(result.size(), 2, "Should return 2 events");
        assertEquals(result.get(0).getEventType(), "ORDER_PLACED");
        assertEquals(result.get(1).getEventType(), "ORDER_FILLED");
    }

    @Test(description = "Multiple events for same order are all saved independently")
    public void testMultipleEventsForSameOrder() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> {
            AuditEvent e = inv.getArgument(0);
            e = AuditEvent.builder().id(UUID.randomUUID())
                    .eventType(e.getEventType())
                    .orderId(orderId)
                    .userId(userId)
                    .sourceService(e.getSourceService())
                    .payload(e.getPayload())
                    .createdAt(Instant.now())
                    .build();
            return e;
        });

        auditService.logEvent("ORDER_PLACED", "order-service", orderId, userId, Map.of(), "topic", 1L);
        auditService.logEvent("COMPLIANCE_APPROVED", "compliance-service", orderId, userId, Map.of(), "topic", 2L);
        auditService.logEvent("ORDER_FILLED", "order-service", orderId, userId, Map.of(), "topic", 3L);

        verify(auditEventRepository, times(3)).save(any(AuditEvent.class));
    }
}
