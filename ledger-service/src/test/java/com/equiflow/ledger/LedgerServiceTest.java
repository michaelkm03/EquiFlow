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

    private static final UUID USER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
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

    // --- hold ---

    @Test(description = "Normal hold on $100,000 account for $1,500")
    public void hold_reducesAvailableCash() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), BigDecimal.ZERO)));

        var response = ledgerService.hold(holdRequest(new BigDecimal("1500.00")));

        assertNotNull(response);
        assertEquals(response.getCashOnHold(), new BigDecimal("1500.00"));
        assertEquals(response.getAvailableCash(), new BigDecimal("98500.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx ->
                "HOLD".equals(tx.getType()) && tx.getAmount().equals(new BigDecimal("1500.00"))));
    }

    @Test(description = "Hold amount exactly equals availableCash — boundary case")
    public void hold_exactAvailableBalance_succeeds() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("1500.00"), BigDecimal.ZERO)));

        var response = ledgerService.hold(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getAvailableCash().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getCashOnHold(), new BigDecimal("1500.00"));
    }

    @Test(description = "Hold exceeds availableCash throws IllegalStateException — balance unchanged",
          expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = ".*Insufficient available funds.*")
    public void hold_insufficientFunds_throwsException() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("500.00"), BigDecimal.ZERO)));

        try {
            ledgerService.hold(holdRequest(new BigDecimal("1500.00")));
        } finally {
            verify(accountRepository, never()).save(any());
        }
    }

    // --- release ---

    @Test(description = "Release $1,500 after hold restores availableCash")
    public void release_restoresAvailableCash() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        assertEquals(response.getAvailableCash(), new BigDecimal("100000.00"));
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "RELEASE".equals(tx.getType())));
    }

    @Test(description = "Release with no existing hold floors at zero — safe for saga compensation")
    public void release_noExistingHold_isIdempotent() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), BigDecimal.ZERO)));

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
    }

    // -------------------------------------------------------------------------
    // EQ-113b — release idempotency
    // SagaRecoveryJob may retry compensation; second release must not mutate state
    // -------------------------------------------------------------------------

    @Test(description = "First release reduces hold and writes RELEASE transaction")
    public void release_firstCall_reducesHoldAndWritesTransaction() {
        // A RELEASE transaction does not yet exist for this orderId — normal path executes
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));
        when(transactionRepository.existsByOrderIdAndType(ORDER_ID, "RELEASE")).thenReturn(false);

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0,
                "cashOnHold should be fully released");
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "RELEASE".equals(tx.getType())));
    }

    @Test(description = "Duplicate release with same orderId is a no-op — no balance change, no second transaction")
    public void release_duplicateOrderId_isNoOp() {
        // A RELEASE transaction already exists — idempotency guard returns early
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), BigDecimal.ZERO)));
        when(transactionRepository.existsByOrderIdAndType(ORDER_ID, "RELEASE")).thenReturn(true);

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        // cashOnHold unchanged — no second release applied
        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test(description = "Null orderId skips idempotency guard — manual adjustments always execute")
    public void release_nullOrderId_alwaysExecutes() {
        // No orderId means this is a manual operator adjustment, not saga-driven — never deduplicated
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        HoldRequest request = new HoldRequest(USER_ID, null, new BigDecimal("1500.00"), "manual adjustment");
        var response = ledgerService.release(request);

        // Guard skipped — release executed normally
        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        verify(accountRepository).save(any());
        verify(transactionRepository, never()).existsByOrderIdAndType(any(), any());
    }

    @Test(description = "Release amount exceeds hold — cashOnHold floors at zero, no negative balance")
    public void release_holdLessThanAmount_floorsAtZero() {
        // cashOnHold = $500, releasing $1,500 — .max(ZERO) prevents negative hold
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("500.00"))));
        when(transactionRepository.existsByOrderIdAndType(ORDER_ID, "RELEASE")).thenReturn(false);

        var response = ledgerService.release(holdRequest(new BigDecimal("1500.00")));

        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0,
                "cashOnHold must not go negative");
        // availableCash increases by the actual hold released ($500), not the requested $1,500
        assertEquals(response.getAvailableCash(), new BigDecimal("100000.00"));
    }

    // --- debit ---

    @Test(description = "Debit reduces cashBalance and releases matching hold")
    public void debit_reducesCashBalanceAndReleasesHold() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        var response = ledgerService.debit(debitRequest(new BigDecimal("1500.00"), null, null));

        assertEquals(response.getCashBalance(), new BigDecimal("98500.00"));
        assertEquals(response.getCashOnHold().compareTo(BigDecimal.ZERO), 0);
        verify(accountRepository).save(any());
        verify(transactionRepository).save(argThat(tx -> "DEBIT".equals(tx.getType())));
    }

    @Test(description = "Debit without ticker does not touch positions")
    public void debit_withoutTicker_noPositionUpsert() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));

        ledgerService.debit(debitRequest(new BigDecimal("1500.00"), null, null));

        verify(positionRepository, never()).save(any());
    }

    @Test(description = "Debit when cashBalance is insufficient throws exception",
          expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = ".*Insufficient funds.*")
    public void debit_insufficientBalance_throwsException() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100.00"), new BigDecimal("100.00"))));

        ledgerService.debit(debitRequest(new BigDecimal("1500.00"), null, null));
    }

    // --- updatePosition (exercised via debit) ---

    @Test(description = "First BUY creates new position with correct avgCost")
    public void updatePosition_newBuy_createsPositionWithAvgCost() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("1500.00"))));
        when(positionRepository.findByUserIdAndTicker(USER_ID, "AAPL"))
                .thenReturn(Optional.empty());

        // $1,500 for 10 shares → avgCost = $150.0000
        ledgerService.debit(debitRequest(new BigDecimal("1500.00"), "AAPL", new BigDecimal("10")));

        verify(positionRepository).save(argThat(p ->
                p.getQuantity().equals(new BigDecimal("10")) &&
                p.getAverageCost().compareTo(new BigDecimal("150.0000")) == 0));
    }

    @Test(description = "Second BUY on existing position sums quantity and recalculates avgCost")
    public void updatePosition_existingBuy_sumsQuantityAndRecalculatesAvgCost() {
        when(accountRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(new BigDecimal("100000.00"), new BigDecimal("750.00"))));
        when(positionRepository.findByUserIdAndTicker(USER_ID, "AAPL"))
                .thenReturn(Optional.of(position(new BigDecimal("10"), new BigDecimal("150.00"))));

        // Buy 5 more shares for $750 → newQty=15, avgCost = $750/15 = $50.0000
        ledgerService.debit(debitRequest(new BigDecimal("750.00"), "AAPL", new BigDecimal("5")));

        verify(positionRepository).save(argThat(p ->
                p.getQuantity().equals(new BigDecimal("15")) &&
                p.getAverageCost().compareTo(new BigDecimal("50.0000")) == 0));
    }

    // NOTE: updatePosition SELL branch (isBuy=false) is not reachable via the public API —
    // debit() always passes isBuy=true. A credit/sell endpoint would be needed to test it.

    // --- getAccount ---

    @Test(description = "getAccount throws when user has no account",
          expectedExceptions = IllegalArgumentException.class)
    public void getAccount_notFound_throwsException() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        ledgerService.getAccount(USER_ID);
    }

    // --- helpers ---

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
