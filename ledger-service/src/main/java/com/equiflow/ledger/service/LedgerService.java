package com.equiflow.ledger.service;

import com.equiflow.ledger.dto.AccountResponse;
import com.equiflow.ledger.dto.DebitRequest;
import com.equiflow.ledger.dto.HoldRequest;
import com.equiflow.ledger.model.Account;
import com.equiflow.ledger.model.LedgerTransaction;
import com.equiflow.ledger.model.Position;
import com.equiflow.ledger.repository.AccountRepository;
import com.equiflow.ledger.repository.LedgerTransactionRepository;
import com.equiflow.ledger.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final LedgerTransactionRepository transactionRepository;

    public AccountResponse getAccount(UUID userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for user: " + userId));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse hold(HoldRequest request) {
        Account account = accountRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.getUserId()));

        BigDecimal available = account.getAvailableCash();
        if (available.compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException(String.format(
                    "Insufficient available funds. Available: $%.2f, Requested hold: $%.2f",
                    available, request.getAmount()));
        }

        account.setCashOnHold(account.getCashOnHold().add(request.getAmount()));
        accountRepository.save(account);

        transactionRepository.save(LedgerTransaction.builder()
                .userId(request.getUserId())
                .orderId(request.getOrderId())
                .type("HOLD")
                .amount(request.getAmount())
                .description(request.getDescription() != null ? request.getDescription() :
                        "Hold placed for order " + request.getOrderId())
                .build());

        log.info("Hold of ${} placed for user {} order {}", request.getAmount(),
                request.getUserId(), request.getOrderId());
        return toResponse(account);
    }

    @Transactional
    public AccountResponse release(HoldRequest request) {
        Account account = accountRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.getUserId()));

        BigDecimal newHold = account.getCashOnHold().subtract(request.getAmount())
                .max(BigDecimal.ZERO);
        account.setCashOnHold(newHold);
        accountRepository.save(account);

        transactionRepository.save(LedgerTransaction.builder()
                .userId(request.getUserId())
                .orderId(request.getOrderId())
                .type("RELEASE")
                .amount(request.getAmount())
                .description("Hold released for order " + request.getOrderId())
                .build());

        log.info("Hold of ${} released for user {} order {}", request.getAmount(),
                request.getUserId(), request.getOrderId());
        return toResponse(account);
    }

    @Transactional
    public AccountResponse debit(DebitRequest request) {
        Account account = accountRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.getUserId()));

        if (account.getCashBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException(String.format(
                    "Insufficient funds. Balance: $%.2f, Required: $%.2f",
                    account.getCashBalance(), request.getAmount()));
        }

        account.setCashBalance(account.getCashBalance().subtract(request.getAmount()));
        // Also release the corresponding hold
        BigDecimal newHold = account.getCashOnHold().subtract(request.getAmount()).max(BigDecimal.ZERO);
        account.setCashOnHold(newHold);
        accountRepository.save(account);

        transactionRepository.save(LedgerTransaction.builder()
                .userId(request.getUserId())
                .orderId(request.getOrderId())
                .type("DEBIT")
                .amount(request.getAmount())
                .description(request.getDescription() != null ? request.getDescription() :
                        "Debit for order " + request.getOrderId())
                .build());

        // Update position if ticker provided
        if (request.getTicker() != null && request.getQuantity() != null) {
            updatePosition(request.getUserId(), request.getTicker(),
                    request.getQuantity(), request.getAmount(), true);
        }

        log.info("Debited ${} from user {} for order {}", request.getAmount(),
                request.getUserId(), request.getOrderId());
        return toResponse(account);
    }

    public List<LedgerTransaction> getHistory(UUID userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Position> getPositions(UUID userId) {
        return positionRepository.findByUserId(userId);
    }

    private void updatePosition(UUID userId, String ticker, BigDecimal quantity,
                                BigDecimal totalCost, boolean isBuy) {
        Position position = positionRepository.findByUserIdAndTicker(userId, ticker)
                .orElse(Position.builder()
                        .userId(userId)
                        .ticker(ticker)
                        .quantity(BigDecimal.ZERO)
                        .averageCost(BigDecimal.ZERO)
                        .build());

        if (isBuy) {
            BigDecimal newQty = position.getQuantity().add(quantity);
            BigDecimal newAvgCost = newQty.compareTo(BigDecimal.ZERO) > 0
                    ? totalCost.divide(newQty, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            position.setQuantity(newQty);
            position.setAverageCost(newAvgCost);
        } else {
            position.setQuantity(position.getQuantity().subtract(quantity).max(BigDecimal.ZERO));
        }

        positionRepository.save(position);
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .userId(account.getUserId())
                .cashBalance(account.getCashBalance())
                .cashOnHold(account.getCashOnHold())
                .availableCash(account.getAvailableCash())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
