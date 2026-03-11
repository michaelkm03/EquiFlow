package com.equiflow.compliance.service;

import com.equiflow.compliance.dto.ComplianceRequest;
import com.equiflow.compliance.dto.ComplianceResult;
import com.equiflow.compliance.dto.Violation;
import com.equiflow.compliance.kafka.ComplianceEventPublisher;
import com.equiflow.compliance.model.ComplianceCheck;
import com.equiflow.compliance.repository.ComplianceCheckRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceCheckRepository checkRepository;
    private final WashSaleService washSaleService;
    private final ComplianceEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public ComplianceResult check(ComplianceRequest request) {
        log.info("Running compliance check for order {} user {}", request.getOrderId(), request.getUserId());

        List<Violation> violations = new ArrayList<>();

        // Rule 1: Insufficient funds check (BUY orders)
        if ("BUY".equalsIgnoreCase(request.getSide())) {
            Optional<Violation> fundsViolation = checkFunds(request);
            fundsViolation.ifPresent(violations::add);
        }

        // Rule 2: Wash-sale check (BUY orders within 30 days of a loss sale)
        if ("BUY".equalsIgnoreCase(request.getSide())) {
            Optional<Violation> washSaleViolation = washSaleService.checkViolation(
                    request.getUserId(), request.getTicker());
            washSaleViolation.ifPresent(violations::add);
        }

        boolean approved = violations.stream()
                .noneMatch(v -> "HARD_BLOCK".equals(v.getSeverity()));

        String violationsJson;
        try {
            violationsJson = objectMapper.writeValueAsString(violations);
        } catch (Exception e) {
            violationsJson = "[]";
        }

        ComplianceCheck check = ComplianceCheck.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .ticker(request.getTicker())
                .result(approved ? "APPROVED" : "REJECTED")
                .violations(violationsJson)
                .build();
        check = checkRepository.save(check);

        ComplianceResult result = ComplianceResult.builder()
                .checkId(check.getId())
                .orderId(request.getOrderId())
                .approved(approved)
                .violations(violations)
                .message(approved ? "Order approved by compliance" :
                        "Order rejected: " + violations.stream()
                                .map(Violation::getCode)
                                .reduce((a, b) -> a + ", " + b).orElse("violations detected"))
                .checkedAt(Instant.now())
                .build();

        if (approved) {
            eventPublisher.publishApproved(request.getOrderId(), request.getUserId());
        } else {
            eventPublisher.publishRejected(request.getOrderId(), request.getUserId(), violations);
        }

        log.info("Compliance check for order {}: {}", request.getOrderId(), result.isApproved() ? "APPROVED" : "REJECTED");
        return result;
    }

    private Optional<Violation> checkFunds(ComplianceRequest request) {
        if (request.getAvailableCash() == null || request.getEstimatedValue() == null) {
            return Optional.empty(); // Can't check without data
        }

        if (request.getAvailableCash().compareTo(request.getEstimatedValue()) < 0) {
            BigDecimal shortfall = request.getEstimatedValue().subtract(request.getAvailableCash());
            return Optional.of(Violation.builder()
                    .code("INSUFFICIENT_FUNDS")
                    .description(String.format(
                            "Insufficient funds: Required $%.2f, Available $%.2f, Shortfall $%.2f",
                            request.getEstimatedValue(), request.getAvailableCash(), shortfall))
                    .severity("HARD_BLOCK")
                    .build());
        }

        return Optional.empty();
    }

    public List<ComplianceCheck> getHistoryForUser(String userId) {
        try {
            return checkRepository.findByUserId(java.util.UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
