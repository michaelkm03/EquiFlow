package com.equiflow.saga.orchestration;

import com.equiflow.saga.model.Saga;
import com.equiflow.saga.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * SagaRecoveryJob: periodically finds sagas stuck in COMPENSATING and re-runs
 * whichever compensation steps have not yet COMPLETED.
 *
 * A saga enters COMPENSATING immediately before any Feign compensation call (EQ-113a checkpoint).
 * If the pod crashes or a Feign call times out, the saga stays COMPENSATING indefinitely.
 * This job picks them up after a 2-minute grace period and retries via OrderSaga helpers.
 *
 * Idempotency: compensateCancel/compensateRelease each check saga.getSteps() for an existing
 * COMPLETED step before calling Feign — safe to re-run multiple times.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoveryJob {

    private static final int STUCK_THRESHOLD_MINUTES = 2;

    private final SagaRepository sagaRepository;
    private final OrderSaga orderSaga;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStuckSagas() {
        Instant cutoff = Instant.now().minus(STUCK_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<Saga> stuck = sagaRepository.findByStatusAndUpdatedAtBefore("COMPENSATING", cutoff);

        if (stuck.isEmpty()) {
            return;
        }

        log.info("saga_recovery found={} stuck COMPENSATING sagas older than {}m", stuck.size(), STUCK_THRESHOLD_MINUTES);

        for (Saga saga : stuck) {
            try {
                log.info("saga_recovery retrying orderId={} currentStep={}", saga.getOrderId(), saga.getCurrentStep());
                orderSaga.rerunCompensation(saga);
            } catch (Exception e) {
                log.error("saga_recovery failed orderId={} reason={}", saga.getOrderId(), e.getMessage());
            }
        }
    }
}
