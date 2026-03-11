package com.equiflow.ledger.controller;

import com.equiflow.ledger.dto.AccountResponse;
import com.equiflow.ledger.dto.DebitRequest;
import com.equiflow.ledger.dto.HoldRequest;
import com.equiflow.ledger.model.LedgerTransaction;
import com.equiflow.ledger.model.Position;
import com.equiflow.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger", description = "Account balances, positions, and transaction history")
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/accounts/{userId}")
    @Operation(summary = "Get account details for a user")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID userId) {
        return ResponseEntity.ok(ledgerService.getAccount(userId));
    }

    @PostMapping("/hold")
    @Operation(summary = "Place a hold on funds (reserves for pending order)")
    public ResponseEntity<AccountResponse> hold(@Valid @RequestBody HoldRequest request) {
        return ResponseEntity.ok(ledgerService.hold(request));
    }

    @PostMapping("/release")
    @Operation(summary = "Release a hold on funds (e.g., when order is cancelled)")
    public ResponseEntity<AccountResponse> release(@Valid @RequestBody HoldRequest request) {
        return ResponseEntity.ok(ledgerService.release(request));
    }

    @PostMapping("/debit")
    @Operation(summary = "Debit funds from account (on order fill)")
    public ResponseEntity<AccountResponse> debit(@Valid @RequestBody DebitRequest request) {
        return ResponseEntity.ok(ledgerService.debit(request));
    }

    @GetMapping("/history/{userId}")
    @Operation(summary = "Get transaction history for a user")
    public ResponseEntity<List<LedgerTransaction>> getHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(ledgerService.getHistory(userId));
    }

    @GetMapping("/positions/{userId}")
    @Operation(summary = "Get all positions for a user")
    public ResponseEntity<List<Position>> getPositions(@PathVariable UUID userId) {
        return ResponseEntity.ok(ledgerService.getPositions(userId));
    }
}
