package com.equiflow.compliance;

import com.equiflow.compliance.dto.ComplianceRequest;
import com.equiflow.compliance.dto.ComplianceResult;
import com.equiflow.compliance.dto.Violation;
import com.equiflow.compliance.kafka.ComplianceEventPublisher;
import com.equiflow.compliance.model.ComplianceCheck;
import com.equiflow.compliance.repository.ComplianceCheckRepository;
import com.equiflow.compliance.service.ComplianceService;
import com.equiflow.compliance.service.WashSaleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ComplianceServiceTest {

    private ComplianceCheckRepository checkRepository;
    private WashSaleService washSaleService;
    private ComplianceEventPublisher eventPublisher;
    private ComplianceService complianceService;

    @BeforeMethod
    public void setUp() {
        checkRepository = mock(ComplianceCheckRepository.class);
        washSaleService = mock(WashSaleService.class);
        eventPublisher = mock(ComplianceEventPublisher.class);
        complianceService = new ComplianceService(
                checkRepository, washSaleService, eventPublisher, new ObjectMapper());

        ComplianceCheck savedCheck = ComplianceCheck.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .ticker("AAPL")
                .result("APPROVED")
                .violations("[]")
                .build();
        when(checkRepository.save(any(ComplianceCheck.class))).thenReturn(savedCheck);
    }

    @Test(description = "Order with wash-sale violation is rejected")
    public void testWashSaleBlock() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(washSaleService.checkViolation(userId, "AAPL")).thenReturn(
                Optional.of(Violation.builder()
                        .code("WASH_SALE")
                        .description("Wash-sale rule violation")
                        .severity("HARD_BLOCK")
                        .build())
        );

        ComplianceRequest request = new ComplianceRequest(
                orderId, userId, "AAPL", "BUY",
                new BigDecimal("10"), new BigDecimal("1500.00"), new BigDecimal("50000.00")
        );

        ComplianceResult result = complianceService.check(request);

        assertFalse(result.isApproved(), "Order with wash-sale should be rejected");
        assertFalse(result.getViolations().isEmpty(), "Should have violations");
        assertEquals(result.getViolations().get(0).getCode(), "WASH_SALE");
        verify(eventPublisher, times(1)).publishRejected(eq(orderId), eq(userId), any());
    }

    @Test(description = "Order with insufficient funds is rejected")
    public void testInsufficientFundsBlock() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(washSaleService.checkViolation(any(), any())).thenReturn(Optional.empty());

        ComplianceRequest request = new ComplianceRequest(
                orderId, userId, "AAPL", "BUY",
                new BigDecimal("100"), new BigDecimal("18950.00"),
                new BigDecimal("5000.00") // only $5000 available, need $18950
        );

        ComplianceResult result = complianceService.check(request);

        assertFalse(result.isApproved(), "Order with insufficient funds should be rejected");
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> "INSUFFICIENT_FUNDS".equals(v.getCode())),
                "Should have INSUFFICIENT_FUNDS violation");
        verify(eventPublisher, times(1)).publishRejected(eq(orderId), eq(userId), any());
    }

    @Test(description = "Valid order with sufficient funds and no violations is approved")
    public void testValidOrderApproved() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(washSaleService.checkViolation(any(), any())).thenReturn(Optional.empty());

        ComplianceRequest request = new ComplianceRequest(
                orderId, userId, "AAPL", "BUY",
                new BigDecimal("10"), new BigDecimal("1500.00"),
                new BigDecimal("50000.00")
        );

        ComplianceResult result = complianceService.check(request);

        assertTrue(result.isApproved(), "Valid order should be approved");
        assertTrue(result.getViolations().isEmpty(), "Approved order should have no violations");
        verify(eventPublisher, times(1)).publishApproved(eq(orderId), eq(userId));
    }
}
