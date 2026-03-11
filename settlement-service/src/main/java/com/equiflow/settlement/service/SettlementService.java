package com.equiflow.settlement.service;

import com.equiflow.settlement.kafka.SettlementEventPublisher;
import com.equiflow.settlement.model.Settlement;
import com.equiflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final NyseCalendar nyseCalendar;
    private final SettlementEventPublisher eventPublisher;

    public List<Settlement> getPending() {
        return settlementRepository.findByStatus("PENDING_SETTLEMENT");
    }

    public List<Settlement> getHistory(UUID userId) {
        return settlementRepository.findByUserId(userId);
    }

    @Transactional
    public Settlement createSettlement(UUID orderId, UUID userId, String ticker,
                                       String side, BigDecimal quantity,
                                       BigDecimal fillPrice) {
        LocalDate tradeDate = LocalDate.now();
        LocalDate settlementDate = nyseCalendar.getSettlementDate(tradeDate);
        BigDecimal totalAmount = fillPrice.multiply(quantity);

        Settlement settlement = Settlement.builder()
                .orderId(orderId)
                .userId(userId)
                .ticker(ticker)
                .side(side)
                .quantity(quantity)
                .fillPrice(fillPrice)
                .totalAmount(totalAmount)
                .tradeDate(tradeDate)
                .settlementDate(settlementDate)
                .status("PENDING_SETTLEMENT")
                .build();

        settlement = settlementRepository.save(settlement);
        log.info("Settlement created for order {} - settles on {}", orderId, settlementDate);
        return settlement;
    }

    @Transactional
    public int runSettlement() {
        LocalDate today = LocalDate.now();
        List<Settlement> due = settlementRepository
                .findByStatusAndSettlementDateLessThanEqual("PENDING_SETTLEMENT", today);

        int settled = 0;
        for (Settlement s : due) {
            try {
                s.setStatus("SETTLED");
                s.setSettledAt(Instant.now());
                settlementRepository.save(s);
                eventPublisher.publishSettled(s);
                settled++;
                log.info("Settled: orderId={} amount={}", s.getOrderId(), s.getTotalAmount());
            } catch (Exception e) {
                s.setStatus("FAILED");
                s.setFailureReason(e.getMessage());
                settlementRepository.save(s);
                log.error("Settlement failed for order {}: {}", s.getOrderId(), e.getMessage());
            }
        }

        log.info("Settlement run completed: {} records settled", settled);
        return settled;
    }
}
