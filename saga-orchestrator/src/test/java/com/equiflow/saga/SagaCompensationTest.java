package com.equiflow.saga;

import com.equiflow.saga.client.ComplianceClient;
import com.equiflow.saga.client.LedgerClient;
import com.equiflow.saga.client.OrderClient;
import com.equiflow.saga.client.SettlementClient;
import com.equiflow.saga.model.Saga;
import com.equiflow.saga.model.SagaStep;
import com.equiflow.saga.orchestration.OrderSaga;
import com.equiflow.saga.repository.SagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SagaCompensationTest {

    private static final String DEFAULT_TICKER = "AAPL";
    private static final String DEFAULT_SIDE = "BUY";
    private static final String DEFAULT_QUANTITY = "10";
    private static final String DEFAULT_LIMIT_PRICE = "150.00";
    private static final double DEFAULT_AVAILABLE_CASH = 10000.0;

    private SagaRepository sagaRepository;
    private ComplianceClient complianceClient;
    private OrderClient orderClient;
    private LedgerClient ledgerClient;
    private SettlementClient settlementClient;
    private OrderSaga orderSaga;

    @BeforeMethod
    public void setUp() {
        sagaRepository = mock(SagaRepository.class);
        complianceClient = mock(ComplianceClient.class);
        orderClient = mock(OrderClient.class);
        ledgerClient = mock(LedgerClient.class);
        settlementClient = mock(SettlementClient.class);

        orderSaga = new OrderSaga(sagaRepository, complianceClient, orderClient,
                ledgerClient, settlementClient, new ObjectMapper());

        when(sagaRepository.save(any(Saga.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── EQ-113a: COMPENSATING checkpoint ────────────────────────────────────────

    @Test(description = "COMPENSATING status is saved before any downstream Feign call")
    public void failSaga_setsCompensatingBeforeFeign() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Saga saga = buildSaga(orderId, userId);

        // Capture each status value at the moment save() is called — Saga is mutable,
        // so inspecting the object after-the-fact would only reflect its final state.
        List<String> savedStatuses = new ArrayList<>();
        when(sagaRepository.save(any(Saga.class))).thenAnswer(inv -> {
            savedStatuses.add(((Saga) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        });

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenThrow(new RuntimeException("compliance service unavailable"));

        orderSaga.execute(saga, buildOrderEvent(orderId, userId));

        assertTrue(savedStatuses.contains("COMPENSATING"),
                "sagaRepository.save() must be called with status=COMPENSATING");
        assertTrue(savedStatuses.indexOf("COMPENSATING") < savedStatuses.lastIndexOf("FAILED"),
                "COMPENSATING must be saved before FAILED");
        verify(orderClient, never()).triggerMatch(any());
        verify(settlementClient, never()).createSettlement(any());
    }

    // ── EQ-113c: Compensation per failed step ───────────────────────────────────

    @Test(description = "Step 1 failure — no Feign calls made, no COMPENSATION_* SagaSteps written")
    public void failSaga_step1_noCompensationCalls() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Saga saga = buildSaga(orderId, userId);

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenThrow(new RuntimeException("compliance unavailable"));

        Saga result = orderSaga.execute(saga, buildOrderEvent(orderId, userId));

        assertEquals(result.getStatus(), "FAILED");
        verify(orderClient, never()).systemCancelOrder(any(), any());
        verify(ledgerClient, never()).release(any());

        long compSteps = result.getSteps().stream()
                .filter(s -> s.getStepName() != null && s.getStepName().startsWith("COMPENSATION_"))
                .count();
        assertEquals(compSteps, 0L, "No COMPENSATION_* steps should be written for step 1 failure");
    }

    @Test(description = "Step 2 failure — cancelOrder called once with correct orderId+userId; COMPENSATION_CANCEL=COMPLETED")
    public void failSaga_step2_cancelOrderCalled() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Saga saga = buildSaga(orderId, userId);

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenReturn(Map.of("approved", true));
        when(orderClient.triggerMatch(any())).thenThrow(new RuntimeException("order service timeout"));
        when(orderClient.systemCancelOrder(any(), any())).thenReturn(Map.of("status", "CANCELLED"));

        Saga result = orderSaga.execute(saga, buildOrderEvent(orderId, userId));

        assertEquals(result.getStatus(), "FAILED");
        verify(ledgerClient, never()).release(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cancelBodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(orderClient, times(1)).systemCancelOrder(eq(orderId), cancelBodyCaptor.capture());
        assertEquals(cancelBodyCaptor.getValue().get("userId"), userId.toString());

        Optional<SagaStep> cancelStep = findCompStep(result, "COMPENSATION_CANCEL");
        assertTrue(cancelStep.isPresent(), "COMPENSATION_CANCEL SagaStep must be written");
        assertEquals(cancelStep.get().getStatus(), "COMPLETED");
        assertFalse(findCompStep(result, "COMPENSATION_RELEASE").isPresent(),
                "COMPENSATION_RELEASE must not be written for step 2 failure");
    }

    @Test(description = "Step 3 failure — cancelOrder and release both called; both SagaSteps COMPLETED")
    public void failSaga_step3_cancelAndReleaseBothCalled() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Saga saga = buildSaga(orderId, userId);

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenReturn(Map.of("approved", true));
        when(orderClient.triggerMatch(any())).thenReturn(filledMatchResponse());
        when(ledgerClient.debit(any())).thenThrow(new RuntimeException("ledger timeout"));
        when(orderClient.systemCancelOrder(any(), any())).thenReturn(Map.of("status", "CANCELLED"));
        when(ledgerClient.release(any())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));

        Saga result = orderSaga.execute(saga, buildOrderEvent(orderId, userId));

        assertEquals(result.getStatus(), "FAILED");
        verify(orderClient, times(1)).systemCancelOrder(eq(orderId), any());
        verify(ledgerClient, times(1)).release(any());

        assertCompStep(result, "COMPENSATION_CANCEL", "COMPLETED");
        assertCompStep(result, "COMPENSATION_RELEASE", "COMPLETED");
    }

    // ── EQ-113c: Partial Feign failure ──────────────────────────────────────────

    @Test(description = "Cancel Feign throws — COMPENSATION_CANCEL=FAILED; release still runs and completes")
    public void failSaga_cancelFails_releaseStillRuns() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Saga saga = buildSaga(orderId, userId);

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenReturn(Map.of("approved", true));
        when(orderClient.triggerMatch(any())).thenReturn(filledMatchResponse());
        when(ledgerClient.debit(any())).thenThrow(new RuntimeException("ledger timeout"));
        when(orderClient.systemCancelOrder(any(), any())).thenThrow(new RuntimeException("order service down"));
        when(ledgerClient.release(any())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));

        Saga result = orderSaga.execute(saga, buildOrderEvent(orderId, userId));

        assertEquals(result.getStatus(), "FAILED");
        verify(ledgerClient, times(1)).release(any());

        SagaStep cancelStep = findCompStep(result, "COMPENSATION_CANCEL").orElseThrow();
        assertEquals(cancelStep.getStatus(), "FAILED");
        assertNotNull(cancelStep.getErrorMessage());

        assertCompStep(result, "COMPENSATION_RELEASE", "COMPLETED");
    }

    @Test(description = "Release Feign throws — COMPENSATION_RELEASE=FAILED; cancel result is unaffected")
    public void failSaga_releaseFails_cancelUnaffected() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Saga saga = buildSaga(orderId, userId);

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenReturn(Map.of("approved", true));
        when(orderClient.triggerMatch(any())).thenReturn(filledMatchResponse());
        when(ledgerClient.debit(any())).thenThrow(new RuntimeException("ledger timeout"));
        when(orderClient.systemCancelOrder(any(), any())).thenReturn(Map.of("status", "CANCELLED"));
        when(ledgerClient.release(any())).thenThrow(new RuntimeException("ledger release failed"));

        Saga result = orderSaga.execute(saga, buildOrderEvent(orderId, userId));

        assertEquals(result.getStatus(), "FAILED");

        assertCompStep(result, "COMPENSATION_CANCEL", "COMPLETED");

        SagaStep releaseStep = findCompStep(result, "COMPENSATION_RELEASE").orElseThrow();
        assertEquals(releaseStep.getStatus(), "FAILED");
        assertNotNull(releaseStep.getErrorMessage());
    }

    // ── EQ-113c: Idempotency guards ─────────────────────────────────────────────

    @Test(description = "COMPENSATION_CANCEL already COMPLETED — cancelOrder skipped; release still runs")
    public void failSaga_cancelAlreadyCompleted_skipsCancel() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SagaStep existingCancel = new SagaStep();
        existingCancel.setStepName("COMPENSATION_CANCEL");
        existingCancel.setStatus("COMPLETED");
        existingCancel.setStepNumber(0);

        Saga saga = buildSagaWithSteps(orderId, userId, 3, List.of(existingCancel));

        when(ledgerClient.release(any())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));

        orderSaga.rerunCompensation(saga);

        verify(orderClient, never()).systemCancelOrder(any(), any());
        verify(ledgerClient, times(1)).release(any());

        assertCompStep(saga, "COMPENSATION_RELEASE", "COMPLETED");
    }

    @Test(description = "COMPENSATION_RELEASE already COMPLETED — release skipped; saga reaches FAILED")
    public void failSaga_releaseAlreadyCompleted_skipsRelease() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SagaStep existingRelease = new SagaStep();
        existingRelease.setStepName("COMPENSATION_RELEASE");
        existingRelease.setStatus("COMPLETED");
        existingRelease.setStepNumber(0);

        Saga saga = buildSagaWithSteps(orderId, userId, 3, List.of(existingRelease));

        when(orderClient.systemCancelOrder(any(), any())).thenReturn(Map.of("status", "CANCELLED"));

        orderSaga.rerunCompensation(saga);

        verify(ledgerClient, never()).release(any());
        assertEquals(saga.getStatus(), "FAILED");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Saga buildSaga(UUID orderId, UUID userId) {
        return Saga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status("STARTED")
                .currentStep(0)
                .startedAt(Instant.now())
                .build();
    }

    private Saga buildSagaWithSteps(UUID orderId, UUID userId, int currentStep, List<SagaStep> steps) {
        return Saga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status("COMPENSATING")
                .currentStep(currentStep)
                .startedAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .steps(new ArrayList<>(steps))
                .build();
    }

    private Map<String, Object> buildOrderEvent(UUID orderId, UUID userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("userId", userId.toString());
        event.put("ticker", DEFAULT_TICKER);
        event.put("side", DEFAULT_SIDE);
        event.put("quantity", DEFAULT_QUANTITY);
        event.put("limitPrice", DEFAULT_LIMIT_PRICE);
        return event;
    }

    private Map<String, Object> filledMatchResponse() {
        return Map.of(
                "status", "FILLED",
                "filledPrice", DEFAULT_LIMIT_PRICE,
                "filledQty", DEFAULT_QUANTITY,
                "ticker", DEFAULT_TICKER
        );
    }

    private Optional<SagaStep> findCompStep(Saga saga, String stepName) {
        return saga.getSteps().stream()
                .filter(s -> stepName.equals(s.getStepName()))
                .findFirst();
    }

    private void assertCompStep(Saga saga, String stepName, String expectedStatus) {
        Optional<SagaStep> step = findCompStep(saga, stepName);
        assertTrue(step.isPresent(), stepName + " SagaStep must be written");
        assertEquals(step.get().getStatus(), expectedStatus,
                stepName + " expected status=" + expectedStatus);
    }
}
