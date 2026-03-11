package com.equiflow.chaos.controller;

import com.equiflow.chaos.dto.ChaosRequest;
import com.equiflow.chaos.dto.ChaosStatus;
import com.equiflow.chaos.service.ChaosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/chaos")
@RequiredArgsConstructor
@Tag(name = "Chaos Engineering", description = "Chaos injection for resilience testing")
public class ChaosController {

    private final ChaosService chaosService;

    @PostMapping("/start")
    @Operation(
        summary = "Start chaos injection",
        description = "Available modes: NETWORK_LATENCY, DB_FAILURE, BOTH"
    )
    public ResponseEntity<ChaosStatus> start(@Valid @RequestBody ChaosRequest request) {
        return ResponseEntity.ok(chaosService.start(request));
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop active chaos session")
    public ResponseEntity<ChaosStatus> stop() {
        return ResponseEntity.ok(chaosService.stop());
    }

    @GetMapping("/status")
    @Operation(summary = "Get current chaos session status")
    public ResponseEntity<ChaosStatus> getStatus() {
        return ResponseEntity.ok(chaosService.getStatus());
    }
}
