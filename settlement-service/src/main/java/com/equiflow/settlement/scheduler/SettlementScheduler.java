package com.equiflow.settlement.scheduler;

import com.equiflow.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    /**
     * Runs daily at 4PM ET (21:00 UTC) Monday-Friday to process T+1 settlements.
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = "UTC")
    public void runDailySettlement() {
        log.info("Starting scheduled settlement run");
        try {
            int count = settlementService.runSettlement();
            log.info("Scheduled settlement run completed: {} records settled", count);
        } catch (Exception e) {
            log.error("Settlement scheduler error: {}", e.getMessage(), e);
        }
    }
}
