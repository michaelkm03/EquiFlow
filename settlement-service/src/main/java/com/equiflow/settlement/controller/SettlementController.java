package com.equiflow.settlement.controller;

import com.equiflow.settlement.model.Settlement;
import com.equiflow.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/settlement")
@RequiredArgsConstructor
@Tag(name = "Settlement", description = "T+1 settlement processing and history")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/pending")
    @Operation(summary = "Get all pending settlements")
    public ResponseEntity<List<Settlement>> getPending() {
        return ResponseEntity.ok(settlementService.getPending());
    }

    @GetMapping("/history/{userId}")
    @Operation(summary = "Get settlement history for a user")
    public ResponseEntity<List<Settlement>> getHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(settlementService.getHistory(userId));
    }

    @PostMapping("/create")
    @Operation(summary = "Manually create a settlement record (used by saga)")
    public ResponseEntity<Settlement> createSettlement(@RequestBody Map<String, Object> body) {
        UUID orderId = UUID.fromString((String) body.get("orderId"));
        UUID userId = UUID.fromString((String) body.get("userId"));
        String ticker = (String) body.get("ticker");
        String side = (String) body.get("side");
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        BigDecimal fillPrice = new BigDecimal(body.get("fillPrice").toString());

        Settlement settlement = settlementService.createSettlement(
                orderId, userId, ticker, side, quantity, fillPrice);
        return ResponseEntity.ok(settlement);
    }

    @PostMapping("/admin/run")
    @Operation(summary = "Manually trigger settlement run (admin endpoint)")
    public ResponseEntity<Map<String, Object>> runSettlement() {
        int settled = settlementService.runSettlement();
        return ResponseEntity.ok(Map.of("settled", settled, "message", settled + " records settled"));
    }
}
