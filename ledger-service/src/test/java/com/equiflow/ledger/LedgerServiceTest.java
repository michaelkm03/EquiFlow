package com.equiflow.ledger;

import com.equiflow.ledger.dto.DebitRequest;
import com.equiflow.ledger.dto.HoldRequest;
import com.equiflow.ledger.model.Account;
import com.equiflow.ledger.model.Position;
import com.equiflow.ledger.repository.AccountRepository;
import com.equiflow.ledger.repository.LedgerTransactionRepository;
import com.equiflow.ledger.repository.PositionRepository;
import com.equiflow.ledger.service.LedgerService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class LedgerServiceTest {

    private static final UUID USER_ID  = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.randomUUID();

    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private LedgerTransactionRepository transactionRepository;
    private LedgerService ledgerService;

    @BeforeMethod
    public void setUp() {
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        transactionRepository = mock(LedgerTransactionRepository.class);
        ledgerService = new LedgerService(accountRepository, positionRepository, transactionRepository);
    }

    // -------------------------------------------------------------------------
    // hold
    // -------------------------------------------------------------------------

    @Test(description = "Hold reduces availableCash, increases cashOnHold, persists account and transaction")
    public void hold_reducesAvailableCash() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), BigDecimal.ZERO)));

        var response = ledgerService.hold(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold(), new BigDecimal("1500.00"));
        assertEquals(response.getAvailableCash(), new BigDecimal("98500.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx ->
                "HOLD".equals(tx.getType()) && tx.getAmount().equals(new BigDecimal("1500.00"))));
    }

    @Test(description = "Hold exactly equal to availableCash — boundary, availableCash reaches zero")
    public void hold_exactAvailableBalance_succeeds() {
        // Boundary: hold == availableCash; availableCash should floor to exactly zero
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("1500.00"), BigDecimal.ZERO)));

        var response = ledgerService.hold(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getAvailableCash().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getCashOnHold(), new BigDecimal("1500.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "HOLD".equals(tx.getType())));
    }

    @Test(description = "Hold exceeds availableCash — exception thrown, account not saved, no transaction written")
    public void hold_insufficientFunds_throwsException() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("500.00"), BigDecimal.ZERO)));

        try {
            ledgerService.hold(holdRequest(new BigDecimal("1500.00")));
            fail("Expected IllegalStateException for insufficient funds");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Insufficient available funds"), "Wrong message: " + ex.getMessage());
        }

        // Validation fires before any mutation — nothing should be persisted
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // release
    // -------------------------------------------------------------------------

    @Test(description = "Release restores availableCash to pre-hold level, writes RELEASE transaction")
    public void release_restoresAvailableCash() {
        // Account has $1,500 on hold; release it — availableCash should return to full balance
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getAvailableCash(), new BigDecimal("100000.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "RELEASE".equals(tx.getType())));
    }

    @Test(description = "Release when cashOnHold is zero — floors at zero, account saved, transaction written")
    public void release_noExistingHold_floorsAtZero() {
        // No hold exists; .max(ZERO) prevents cashOnHold going negative
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), BigDecimal.ZERO)));

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getAvailableCash(), new BigDecimal("100000.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(any());
    }

    // -------------------------------------------------------------------------
    // EQ-113b — release idempotency
    // SagaRecoveryJob may retry compensation; second release must not mutate state
    // -------------------------------------------------------------------------

    @Test(description = "First release — guard passes (no prior RELEASE), hold reduced, transaction written")
    public void release_firstCall_reducesHoldAndWritesTransaction() {
        // No prior RELEASE transaction exists for this orderId — normal execution path
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));
        when(transactionRepository.existsByOrderIdAndType(ORDER_ID, "RELEASE")).thenReturn(false);

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "RELEASE".equals(tx.getType())));
    }

    @Test(description = "Duplicate release — guard detects prior RELEASE, no balance change, no second transaction")
    public void release_duplicateOrderId_isNoOp() {
        // A RELEASE transaction already exists for this orderId — idempotency guard returns early
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), BigDecimal.ZERO)));
        when(transactionRepository.existsByOrderIdAndType(ORDER_ID, "RELEASE")).thenReturn(true);

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        // Balance and hold are unchanged; caller receives same 200 response as a successful release
        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getAvailableCash(), new BigDecimal("100000.00"));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test(description = "Null orderId — idempotency guard skipped, release always executes, transaction written")
    public void release_nullOrderId_alwaysExecutes() {
        // Manual operator adjustment: no orderId means no deduplication check
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        HoldRequest request = new HoldRequest(USER_ID, null, new BigDecimal("1500.00"), "manual adjustment");
        var response = ledgerService.release(request);

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        verify(accountRepository).save(any());
        // Guard must not be called when orderId is null
        verify(transactionRepository, never()).existsByOrderIdAndType(any(), any());
        // Release transaction is still recorded for audit purposes
        verify(transactionRepository).save(any());
    }

    @Test(description = "Release amount > cashOnHold — cashOnHold floors at zero, availableCash capped at balance")
    public void release_holdLessThanAmount_floorsAtZero() {
        // cashOnHold = $500; releasing $1,500 — only $500 is actually freed, not $1,500
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("500.00"))));
        when(transactionRepository.existsByOrderIdAndType(ORDER_ID, "RELEASE")).thenReturn(false);

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getAvailableCash(), new BigDecimal("100000.00"));
        verify(accountRepository).save(any());
    }

    // -------------------------------------------------------------------------
    // debit
    // -------------------------------------------------------------------------

    @Test(description = "Debit reduces cashBalance, releases hold, persists account and DEBIT transaction")
    public void debit_reducesCashBalanceAndReleasesHold() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        var response = ledgerService.debit(debitRequest(new BigDecimal("1500.00"), null, null));

        assertEquals(response.getCashBalance(), new BigDecimal("98500.00"));
        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "DEBIT".equals(tx.getType())));
    }

    @Test(description = "Debit without ticker — debit completes, position table untouched")
    public void debit_withoutTicker_noPositionUpsert() {
        // No ticker provided — updatePosition is skipped entirely
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        var response = ledgerService.debit(debitRequest(new BigDecimal("1500.00"), null, null));

        // Debit still executes — balance reduced, transaction written
        assertEquals(response.getCashBalance(), new BigDecimal("98500.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(any());
        // Position table must not be touched
        verify(positionRepository, never()).save(any());
    }

    @Test(description = "Debit with insufficient cashBalance — exception thrown, nothing persisted")
    public void debit_insufficientBalance_throwsException() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100.00"), new BigDecimal("100.00"))));

        try {
            ledgerService.debit(debitRequest(new BigDecimal("1500.00"), null, null));
            fail("Expected IllegalStateException for insufficient balance");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Insufficient funds"), "Wrong message: " + ex.getMessage());
        }

        // Validation fires before any mutation
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // updatePosition (exercised via debit)
    // -------------------------------------------------------------------------

    @Test(description = "First BUY — new position created, avgCost = totalCost / quantity")
    public void updatePosition_newBuy_createsPositionWithAvgCost() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));
        when(positionRepository.findByUserIdAndTicker(USER_ID, "AAPL"))
                .thenReturn(Optional.empty());

        // $1,500 for 10 shares → avgCost = $1,500 / 10 = $150.0000
        ledgerService.debit(debitRequest(new BigDecimal("1500.00"), "AAPL", new BigDecimal("10")));

        verify(positionRepository).save(argThat(p ->
                p.getQuantity().equals(new BigDecimal("10")) &&
                p.getAverageCost().compareTo(new BigDecimal("150.0000")) == 0));
    }

    @Test(description = "Second BUY on existing position — qty summed, avgCost = latestPurchaseCost / newTotalQty")
    public void updatePosition_existingBuy_sumsQuantityAndRecalculatesAvgCost() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("750.00"))));
        when(positionRepository.findByUserIdAndTicker(USER_ID, "AAPL"))
                .thenReturn(Optional.of(position(new BigDecimal("10"), new BigDecimal("150.00"))));

        // NOTE: avgCost = latestPurchaseAmount / newTotalQty (not a weighted average).
        // $750 purchase + existing 10 shares → newQty = 15; avgCost = $750 / 15 = $50.0000
        ledgerService.debit(debitRequest(new BigDecimal("750.00"), "AAPL", new BigDecimal("5")));

        verify(positionRepository).save(argThat(p ->
                p.getQuantity().equals(new BigDecimal("15")) &&
                p.getAverageCost().compareTo(new BigDecimal("50.0000")) == 0));
    }

    // NOTE: updatePosition SELL branch (isBuy=false) is not reachable via the current public API —
    // debit() always passes isBuy=true. A credit/sell endpoint would be needed to test it.

    // -------------------------------------------------------------------------
    // getAccount
    // -------------------------------------------------------------------------

    @Test(description = "getAccount for unknown user — throws IllegalArgumentException",
          expectedExceptions = IllegalArgumentException.class)
    public void getAccount_notFound_throwsException() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        ledgerService.getAccount(USER_ID);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Account account(BigDecimal cashBalance, BigDecimal cashOnHold) {
        return Account.builder()
                .userId(USER_ID)
                .cashBalance(cashBalance)
                .cashOnHold(cashOnHold)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Position position(BigDecimal quantity, BigDecimal avgCost) {
        return Position.builder()
                .userId(USER_ID)
                .ticker("AAPL")
                .quantity(quantity)
                .averageCost(avgCost)
                .build();
    }

    private HoldRequest holdRequest(BigDecimal amount) {
        return new HoldRequest(USER_ID, ORDER_ID, amount, null);
    }

    private DebitRequest debitRequest(BigDecimal amount, String ticker, BigDecimal quantity) {
        return new DebitRequest(USER_ID, ORDER_ID, amount, ticker, quantity, null);
    }
}
