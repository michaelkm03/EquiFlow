package com.equiflow.audit.controller;

import com.equiflow.audit.model.AuditEvent;
import com.equiflow.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Append-only event audit trail")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/events")
    @Operation(summary = "Get all audit events (paginated)")
    public ResponseEntity<Page<AuditEvent>> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getAllEvents(page, size));
    }

    @GetMapping("/events/order/{orderId}")
    @Operation(summary = "Get audit trail for a specific order")
    public ResponseEntity<List<AuditEvent>> getEventsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(auditService.getEventsByOrder(orderId));
    }

    @GetMapping("/events/user/{userId}")
    @Operation(summary = "Get audit events for a user (paginated)")
    public ResponseEntity<Page<AuditEvent>> getEventsByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getEventsByUser(userId, page, size));
    }

    @GetMapping("/events/type/{eventType}")
    @Operation(summary = "Get audit events by event type")
    public ResponseEntity<Page<AuditEvent>> getEventsByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getEventsByType(eventType, page, size));
    }

    @GetMapping("/events/range")
    @Operation(summary = "Get audit events within a time range")
    public ResponseEntity<Page<AuditEvent>> getEventsByRange(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getEventsByTimeRange(from, to, page, size));
    }
}
