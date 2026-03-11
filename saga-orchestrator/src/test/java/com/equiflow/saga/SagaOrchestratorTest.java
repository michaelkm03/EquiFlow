package com.equiflow.saga;

import com.equiflow.saga.client.ComplianceClient;
import com.equiflow.saga.client.LedgerClient;
import com.equiflow.saga.client.OrderClient;
import com.equiflow.saga.client.SettlementClient;
import com.equiflow.saga.model.Saga;
import com.equiflow.saga.orchestration.OrderSaga;
import com.equiflow.saga.repository.SagaRepository;
import com.equiflow.saga.service.SagaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SagaOrchestratorTest {

    private SagaRepository sagaRepository;
    private ComplianceClient complianceClient;
    private OrderClient orderClient;
    private LedgerClient ledgerClient;
    private SettlementClient settlementClient;
    private OrderSaga orderSaga;
    private SagaService sagaService;

    @BeforeMethod
    public void setUp() {
        sagaRepository = mock(SagaRepository.class);
        complianceClient = mock(ComplianceClient.class);
        orderClient = mock(OrderClient.class);
        ledgerClient = mock(LedgerClient.class);
        settlementClient = mock(SettlementClient.class);

        orderSaga = new OrderSaga(sagaRepository, complianceClient, orderClient,
                ledgerClient, settlementClient, new ObjectMapper());
        sagaService = new SagaService(sagaRepository, orderSaga);
    }

    @Test(description = "Full saga flow completes successfully with all mocked services")
    public void testFullSagaFlow() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Saga saga = Saga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status("STARTED")
                .currentStep(0)
                .startedAt(Instant.now())
                .build();

        // Mock all downstream service calls
        when(complianceClient.check(any())).thenReturn(Map.of(
                "approved", true, "violations", java.util.List.of()
        ));
        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of(
                "availableCash", 50000.00, "cashBalance", 100000.00
        ));
        when(orderClient.triggerMatch(orderId)).thenReturn(Map.of(
                "id", orderId.toString(),
                "status", "FILLED",
                "filledQty", "10",
                "filledPrice", "150.00",
                "ticker", "AAPL"
        ));
        when(ledgerClient.debit(any())).thenReturn(Map.of(
                "availableCash", 48500.00, "cashBalance", 98500.00
        ));
        when(settlementClient.createSettlement(any())).thenReturn(Map.of(
                "id", UUID.randomUUID().toString(),
                "status", "PENDING_SETTLEMENT"
        ));

        // Saga should save multiple times (each step update + final)
        when(sagaRepository.save(any(Saga.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> orderEvent = Map.of(
                "orderId", orderId.toString(),
                "userId", userId.toString(),
                "ticker", "AAPL",
                "side", "BUY",
                "quantity", "10",
                "limitPrice", "150.00"
        );

        Saga result = orderSaga.execute(saga, orderEvent);

        assertEquals(result.getStatus(), "COMPLETED", "Saga should complete successfully");
        assertNotNull(result.getCompletedAt(), "Completed saga should have completedAt timestamp");
        assertEquals(result.getCurrentStep(), 5, "Saga should be at step 5");

        verify(complianceClient, times(1)).check(any());
        verify(orderClient, times(1)).triggerMatch(orderId);
        verify(ledgerClient, times(1)).debit(any());
        verify(settlementClient, times(1)).createSettlement(any());
    }

    @Test(description = "Saga fails and records failure when compliance rejects")
    public void testSagaFailsOnComplianceRejection() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Saga saga = Saga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status("STARTED")
                .currentStep(0)
                .startedAt(Instant.now())
                .build();

        when(complianceClient.check(any())).thenReturn(Map.of(
                "approved", false,
                "violations", java.util.List.of(Map.of("code", "INSUFFICIENT_FUNDS"))
        ));
        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", 100.00));
        when(sagaRepository.save(any(Saga.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> orderEvent = Map.of(
                "orderId", orderId.toString(),
                "userId", userId.toString(),
                "ticker", "AAPL",
                "side", "BUY",
                "quantity", "1000"
        );

        Saga result = orderSaga.execute(saga, orderEvent);

        assertEquals(result.getStatus(), "FAILED", "Saga should fail on compliance rejection");
        assertNotNull(result.getFailureReason(), "Failed saga should have a failure reason");
        verify(orderClient, never()).triggerMatch(any()); // Should not proceed to matching
    }

    @Test(description = "startSaga creates new saga if none exists")
    public void testStartSagaCreatesNew() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Saga newSaga = Saga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .status("STARTED")
                .currentStep(0)
                .startedAt(Instant.now())
                .build();

        when(sagaRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(sagaRepository.save(any(Saga.class))).thenReturn(newSaga);

        Saga result = sagaService.startSaga(orderId, userId);

        assertNotNull(result);
        assertEquals(result.getStatus(), "STARTED");
        verify(sagaRepository, times(1)).save(any(Saga.class));
    }
}
