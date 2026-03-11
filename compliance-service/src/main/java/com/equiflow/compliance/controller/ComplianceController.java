package com.equiflow.compliance.controller;

import com.equiflow.compliance.dto.ComplianceRequest;
import com.equiflow.compliance.dto.ComplianceResult;
import com.equiflow.compliance.model.ComplianceCheck;
import com.equiflow.compliance.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "Compliance checks for order validation")
public class ComplianceController {

    private final ComplianceService complianceService;

    @PostMapping("/check")
    @Operation(
        summary = "Run compliance check for an order",
        description = "Runs wash-sale and insufficient-funds checks"
    )
    @ApiResponse(responseCode = "200", description = "Check completed")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    public ResponseEntity<ComplianceResult> check(@Valid @RequestBody ComplianceRequest request) {
        return ResponseEntity.ok(complianceService.check(request));
    }

    @GetMapping("/history/{userId}")
    @Operation(summary = "Get compliance check history for a user")
    public ResponseEntity<List<ComplianceCheck>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(complianceService.getHistoryForUser(userId));
    }
}
