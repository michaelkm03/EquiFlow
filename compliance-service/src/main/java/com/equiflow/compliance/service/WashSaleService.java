package com.equiflow.compliance.service;

import com.equiflow.compliance.dto.Violation;
import com.equiflow.compliance.model.WashSaleHistory;
import com.equiflow.compliance.repository.WashSaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WashSaleService {

    private static final int WASH_SALE_WINDOW_DAYS = 30;

    private final WashSaleHistoryRepository washSaleHistoryRepository;

    /**
     * Checks if a BUY order would be a wash-sale violation.
     * Wash-sale: buying the same security within 30 days before/after a loss sale.
     */
    public Optional<Violation> checkViolation(UUID userId, String ticker) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(WASH_SALE_WINDOW_DAYS);
        LocalDate windowEnd = today.plusDays(WASH_SALE_WINDOW_DAYS);

        List<WashSaleHistory> recentSales = washSaleHistoryRepository
                .findRecentSales(userId, ticker, windowStart, windowEnd);

        // Filter for loss sales (where sale_price < cost_basis)
        boolean hasLossSale = recentSales.stream()
                .anyMatch(sale -> sale.getLossAmount() != null &&
                          sale.getLossAmount().compareTo(BigDecimal.ZERO) > 0);

        if (hasLossSale) {
            log.info("Wash-sale violation detected for user {} on ticker {}", userId, ticker);
            return Optional.of(Violation.builder()
                    .code("WASH_SALE")
                    .description(String.format(
                            "Wash-sale rule violation: A loss sale of %s was recorded within 30 days. " +
                            "Repurchasing within this window disallows the tax loss deduction.",
                            ticker))
                    .severity("HARD_BLOCK")
                    .build());
        }

        return Optional.empty();
    }

    @Transactional
    public WashSaleHistory recordLoss(UUID userId, String ticker, UUID orderId,
                                      LocalDate saleDate, BigDecimal salePrice,
                                      BigDecimal costBasis) {
        BigDecimal lossAmount = costBasis != null && salePrice != null
                ? costBasis.subtract(salePrice).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        WashSaleHistory record = WashSaleHistory.builder()
                .userId(userId)
                .ticker(ticker)
                .orderId(orderId)
                .saleDate(saleDate)
                .salePrice(salePrice)
                .costBasis(costBasis)
                .lossAmount(lossAmount)
                .build();

        return washSaleHistoryRepository.save(record);
    }
}
