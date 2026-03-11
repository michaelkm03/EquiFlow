package com.equiflow.saga.controller;

import com.equiflow.saga.model.Saga;
import com.equiflow.saga.service.SagaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/saga")
@RequiredArgsConstructor
@Tag(name = "Saga", description = "Distributed saga orchestration for order workflows")
public class SagaController {

    private final SagaService sagaService;

    @GetMapping("/active")
    @Operation(summary = "Get all active (STARTED) sagas")
    public ResponseEntity<List<Saga>> getActiveSagas() {
        return ResponseEntity.ok(sagaService.getActiveSagas());
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga by ID")
    public ResponseEntity<Saga> getSaga(@PathVariable UUID sagaId) {
        return ResponseEntity.ok(sagaService.getSaga(sagaId));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get saga by order ID")
    public ResponseEntity<Saga> getSagaByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(sagaService.getSagaByOrderId(orderId));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get sagas by status (STARTED, COMPLETED, FAILED)")
    public ResponseEntity<List<Saga>> getSagasByStatus(@PathVariable String status) {
        return ResponseEntity.ok(sagaService.getSagasByStatus(status));
    }
}
