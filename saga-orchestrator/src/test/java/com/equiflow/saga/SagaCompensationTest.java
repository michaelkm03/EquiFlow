package com.equiflow.saga;

import com.equiflow.saga.client.ComplianceClient;
import com.equiflow.saga.client.LedgerClient;
import com.equiflow.saga.client.OrderClient;
import com.equiflow.saga.client.SettlementClient;
import com.equiflow.saga.model.Saga;
import com.equiflow.saga.orchestration.OrderSaga;
import com.equiflow.saga.repository.SagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SagaCompensationTest {

    // Default test data — override per-test when the value is meaningful to that scenario
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
    }

    @Test(description = "failSaga() saves COMPENSATING status before any downstream Feign call is made")
    public void failSaga_setsCompensatingBeforeFeign() {
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

        // Capture each status value at the moment save() is called — Saga is mutable,
        // so inspecting the object after-the-fact would reflect its final state only.
        List<String> savedStatuses = new ArrayList<>();
        when(sagaRepository.save(any(Saga.class))).thenAnswer(inv -> {
            savedStatuses.add(((Saga) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        });

        when(ledgerClient.getAccount(anyString())).thenReturn(Map.of("availableCash", DEFAULT_AVAILABLE_CASH));
        when(complianceClient.check(any())).thenThrow(new RuntimeException("compliance service unavailable"));

        Map<String, Object> orderEvent = Map.of(
                "orderId", orderId.toString(),
                "userId", userId.toString(),
                "ticker", DEFAULT_TICKER,
                "side", DEFAULT_SIDE,
                "quantity", DEFAULT_QUANTITY,
                "limitPrice", DEFAULT_LIMIT_PRICE
        );

        orderSaga.execute(saga, orderEvent);

        // COMPENSATING must appear in the save history before FAILED
        assertTrue(savedStatuses.contains("COMPENSATING"),
                "sagaRepository.save() must be called with status=COMPENSATING");
        assertTrue(savedStatuses.indexOf("COMPENSATING") < savedStatuses.lastIndexOf("FAILED"),
                "COMPENSATING must be saved before FAILED");

        // No downstream Feign calls to order or settlement services
        // (EQ-113c will add them; this verifies the ordering contract holds)
        verify(orderClient, never()).triggerMatch(any());
        verify(settlementClient, never()).createSettlement(any());
    }
}
