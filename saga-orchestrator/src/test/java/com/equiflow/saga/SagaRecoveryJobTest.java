package com.equiflow.saga;

import com.equiflow.saga.client.ComplianceClient;
import com.equiflow.saga.client.LedgerClient;
import com.equiflow.saga.client.OrderClient;
import com.equiflow.saga.client.SettlementClient;
import com.equiflow.saga.model.Saga;
import com.equiflow.saga.model.SagaStep;
import com.equiflow.saga.orchestration.OrderSaga;
import com.equiflow.saga.orchestration.SagaRecoveryJob;
import com.equiflow.saga.repository.SagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SagaRecoveryJobTest {

    private SagaRepository sagaRepository;
    private OrderClient orderClient;
    private LedgerClient ledgerClient;
    private OrderSaga orderSaga;
    private SagaRecoveryJob recoveryJob;

    @BeforeMethod
    public void setUp() {
        sagaRepository = mock(SagaRepository.class);
        orderClient = mock(OrderClient.class);
        ledgerClient = mock(LedgerClient.class);

        // Use real OrderSaga so Feign call verification reflects actual compensation logic
        orderSaga = new OrderSaga(sagaRepository, mock(ComplianceClient.class), orderClient,
                ledgerClient, mock(SettlementClient.class), new ObjectMapper());
        recoveryJob = new SagaRecoveryJob(sagaRepository, orderSaga);

        when(sagaRepository.save(any(Saga.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Scheduling and eligibility ───────────────────────────────────────────────

    @Test(description = "No COMPENSATING sagas returned — no Feign calls made")
    public void recoveryJob_noCompensatingSagas_doesNothing() {
        when(sagaRepository.findByStatusAndUpdatedAtBefore(anyString(), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        recoveryJob.recoverStuckSagas();

        verify(orderClient, never()).systemCancelOrder(any(), any());
        verify(ledgerClient, never()).release(any());
    }

    @Test(description = "Recovery job queries with a 2-minute cutoff — sagas updated < 2 min ago are excluded by the query")
    public void recoveryJob_sagaYoungerThan2Min_isSkipped() {
        when(sagaRepository.findByStatusAndUpdatedAtBefore(eq("COMPENSATING"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        recoveryJob.recoverStuckSagas();

        verify(sagaRepository).findByStatusAndUpdatedAtBefore(eq("COMPENSATING"), cutoffCaptor.capture());
        Instant cutoff = cutoffCaptor.getValue();
        assertTrue(cutoff.isBefore(Instant.now().minus(1, ChronoUnit.MINUTES)),
                "Cutoff must be at least 1 minute in the past");
        assertTrue(cutoff.isAfter(Instant.now().minus(3, ChronoUnit.MINUTES)),
                "Cutoff must be no more than 3 minutes in the past");
        verify(orderClient, never()).systemCancelOrder(any(), any());
    }

    @Test(description = "Saga stuck in COMPENSATING for 5 minutes, currentStep=3 — both cancel and release run")
    public void recoveryJob_sagaOlderThan2Min_runsCompensation() {
        UUID orderId = UUID.randomUUID();
        Saga stuckSaga = buildStuckSaga(orderId, UUID.randomUUID(), 3, Collections.emptyList(),
                Instant.now().minus(5, ChronoUnit.MINUTES));

        when(sagaRepository.findByStatusAndUpdatedAtBefore(anyString(), any(Instant.class)))
                .thenReturn(List.of(stuckSaga));
        when(orderClient.systemCancelOrder(any(), any())).thenReturn(Map.of("status", "CANCELLED"));
        when(ledgerClient.release(any())).thenReturn(Map.of("availableCash", 10000.0));

        recoveryJob.recoverStuckSagas();

        verify(orderClient, times(1)).systemCancelOrder(eq(orderId), any());
        verify(ledgerClient, times(1)).release(any());
        assertEquals(stuckSaga.getStatus(), "FAILED");
    }

    // ── Step-aware recovery ──────────────────────────────────────────────────────

    @Test(description = "COMPENSATION_CANCEL already COMPLETED — cancel skipped; release runs once")
    public void recoveryJob_cancelAlreadyDone_onlyRunsRelease() {
        UUID orderId = UUID.randomUUID();

        SagaStep existingCancel = new SagaStep();
        existingCancel.setStepName("COMPENSATION_CANCEL");
        existingCancel.setStatus("COMPLETED");
        existingCancel.setStepNumber(0);

        Saga stuckSaga = buildStuckSaga(orderId, UUID.randomUUID(), 3, List.of(existingCancel),
                Instant.now().minus(5, ChronoUnit.MINUTES));

        when(sagaRepository.findByStatusAndUpdatedAtBefore(anyString(), any(Instant.class)))
                .thenReturn(List.of(stuckSaga));
        when(ledgerClient.release(any())).thenReturn(Map.of("availableCash", 10000.0));

        recoveryJob.recoverStuckSagas();

        verify(orderClient, never()).systemCancelOrder(any(), any());
        verify(ledgerClient, times(1)).release(any());
    }

    @Test(description = "Both COMPENSATION_CANCEL and COMPENSATION_RELEASE already COMPLETED — no Feign calls; saga marked FAILED")
    public void recoveryJob_bothStepsDone_noCallsMade() {
        SagaStep existingCancel = new SagaStep();
        existingCancel.setStepName("COMPENSATION_CANCEL");
        existingCancel.setStatus("COMPLETED");
        existingCancel.setStepNumber(0);

        SagaStep existingRelease = new SagaStep();
        existingRelease.setStepName("COMPENSATION_RELEASE");
        existingRelease.setStatus("COMPLETED");
        existingRelease.setStepNumber(0);

        Saga stuckSaga = buildStuckSaga(UUID.randomUUID(), UUID.randomUUID(), 3,
                List.of(existingCancel, existingRelease), Instant.now().minus(5, ChronoUnit.MINUTES));

        when(sagaRepository.findByStatusAndUpdatedAtBefore(anyString(), any(Instant.class)))
                .thenReturn(List.of(stuckSaga));

        recoveryJob.recoverStuckSagas();

        verify(orderClient, never()).systemCancelOrder(any(), any());
        verify(ledgerClient, never()).release(any());
        assertEquals(stuckSaga.getStatus(), "FAILED");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Saga buildStuckSaga(UUID orderId, UUID userId, int currentStep,
                                List<SagaStep> preExistingSteps, Instant updatedAt) {
        return Saga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status("COMPENSATING")
                .currentStep(currentStep)
                .startedAt(updatedAt.minus(5, ChronoUnit.MINUTES))
                .updatedAt(updatedAt)
                .steps(new ArrayList<>(preExistingSteps))
                .build();
    }
}
