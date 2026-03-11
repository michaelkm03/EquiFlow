package com.equiflow.market.controller;

import com.equiflow.market.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/market")
@RequiredArgsConstructor
@Tag(name = "Market Admin", description = "Scenario management endpoints for market simulation")
public class MarketAdminController {

    private final MarketDataService marketDataService;

    @PostMapping("/scenarios/{scenarioName}/start")
    @Operation(
        summary = "Start a market scenario",
        description = "Available scenarios: flash_crash, bull_run, bear_market, high_volatility, sector_rotation, liquidity_crisis"
    )
    public ResponseEntity<Map<String, Object>> startScenario(
            @PathVariable String scenarioName,
            Authentication auth) {
        String triggeredBy = auth != null ? auth.getName() : "anonymous";
        return ResponseEntity.ok(marketDataService.triggerScenario(scenarioName, triggeredBy));
    }

    @PostMapping("/scenarios/stop")
    @Operation(summary = "Stop the active scenario")
    public ResponseEntity<Map<String, Object>> stopScenario() {
        return ResponseEntity.ok(marketDataService.stopScenario());
    }

    @GetMapping("/scenarios/status")
    @Operation(summary = "Get scenario engine status")
    public ResponseEntity<Map<String, Object>> getScenarioStatus() {
        return ResponseEntity.ok(marketDataService.getScenarioStatus());
    }
}
