package com.equiflow.saga.orchestration;

import com.equiflow.saga.client.ComplianceClient;
import com.equiflow.saga.client.LedgerClient;
import com.equiflow.saga.client.OrderClient;
import com.equiflow.saga.client.SettlementClient;
import com.equiflow.saga.model.Saga;
import com.equiflow.saga.model.SagaStep;
import com.equiflow.saga.repository.SagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OrderSaga: 5-step distributed saga for order processing.
 * Step 1: Compliance check
 * Step 2: Match order (execute in order book)
 * Step 3: Debit ledger
 * Step 4: Create settlement
 * Step 5: Complete saga
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSaga {

    private final SagaRepository sagaRepository;
    private final ComplianceClient complianceClient;
    private final OrderClient orderClient;
    private final LedgerClient ledgerClient;
    private final SettlementClient settlementClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public Saga execute(Saga saga, Map<String, Object> orderEvent) {
        log.info("Executing OrderSaga for orderId={}", saga.getOrderId());
        final Saga sagaRef = saga;

        // Step 1: Compliance check
        SagaStep step1 = executeStep(saga, 1, "COMPLIANCE_CHECK", () -> {
            Map<String, Object> compRequest = new HashMap<>();
            compRequest.put("orderId", sagaRef.getOrderId().toString());
            compRequest.put("userId", sagaRef.getUserId().toString());
            compRequest.put("ticker", orderEvent.get("ticker"));
            compRequest.put("side", orderEvent.get("side"));
            compRequest.put("quantity", orderEvent.get("quantity"));

            // Fetch available cash for funds check
            try {
                Map<String, Object> account = ledgerClient.getAccount(sagaRef.getUserId().toString());
                compRequest.put("availableCash", account.get("availableCash"));
                Object price = orderEvent.get("limitPrice");
                if (price != null && orderEvent.get("quantity") != null) {
                    double val = Double.parseDouble(price.toString()) *
                            Double.parseDouble(orderEvent.get("quantity").toString());
                    compRequest.put("estimatedValue", val);
                }
            } catch (Exception e) {
                log.warn("Could not fetch ledger data for compliance check: {}", e.getMessage());
            }

            return complianceClient.check(compRequest);
        });

        if ("FAILED".equals(step1.getStatus())) {
            return failSaga(saga, "Compliance check failed: " + step1.getErrorMessage());
        }

        // Verify compliance approved
        String compResult = step1.getResponsePayload();
        if (compResult != null && compResult.contains("\"approved\":false")) {
            return failSaga(saga, "Order rejected by compliance check");
        }

        // Step 2: Match order in order book — skip for STOP_LOSS (awaits price trigger)
        String orderType = (String) orderEvent.get("type");
        if ("STOP_LOSS".equals(orderType)) {
            saga.setStatus("COMPLETED");
            saga.setCurrentStep(5);
            saga.setCompletedAt(Instant.now());
            log.info("OrderSaga skipping execution for STOP_LOSS orderId={} — awaiting price trigger", saga.getOrderId());
            return sagaRepository.save(saga);
        }

        SagaStep step2 = executeStep(saga, 2, "ORDER_MATCHING", () ->
                orderClient.triggerMatch(sagaRef.getOrderId())
        );

        if ("FAILED".equals(step2.getStatus())) {
            // Compensate: nothing to undo for matching yet
            return failSaga(saga, "Order matching failed: " + step2.getErrorMessage());
        }

        // Step 3: Debit ledger (extract fill details from match response)
        String fillPayload = step2.getResponsePayload();
        SagaStep step3 = executeStep(saga, 3, "LEDGER_DEBIT", () -> {
            Map<String, Object> debitReq = new HashMap<>();
            debitReq.put("userId", sagaRef.getUserId().toString());
            debitReq.put("orderId", sagaRef.getOrderId().toString());

            // Parse fill details from order response
            try {
                Map<?, ?> matchResult = objectMapper.readValue(fillPayload, Map.class);
                Object filledPrice = matchResult.get("filledPrice");
                Object filledQty = matchResult.get("filledQty");
                Object ticker = matchResult.get("ticker");

                if (filledPrice != null && filledQty != null) {
                    double amount = Double.parseDouble(filledPrice.toString()) *
                            Double.parseDouble(filledQty.toString());
                    debitReq.put("amount", amount);
                    debitReq.put("ticker", ticker);
                    debitReq.put("quantity", filledQty);
                    debitReq.put("description", "Order fill debit for " + sagaRef.getOrderId());
                }
            } catch (Exception e) {
                log.warn("Could not parse match result, using order event data: {}", e.getMessage());
                debitReq.put("amount", 0);
                debitReq.put("description", "Order fill - amount TBD");
            }
            return ledgerClient.debit(debitReq);
        });

        if ("FAILED".equals(step3.getStatus())) {
            return failSaga(saga, "Ledger debit failed: " + step3.getErrorMessage());
        }

        // Step 4: Create settlement record
        SagaStep step4 = executeStep(saga, 4, "SETTLEMENT_CREATE", () -> {
            Map<String, Object> settlReq = new HashMap<>();
            settlReq.put("orderId", sagaRef.getOrderId().toString());
            settlReq.put("userId", sagaRef.getUserId().toString());
            settlReq.put("ticker", orderEvent.get("ticker"));
            settlReq.put("side", orderEvent.get("side"));

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> matchResult = objectMapper.readValue(fillPayload, Map.class);
                settlReq.put("quantity", matchResult.getOrDefault("filledQty", orderEvent.get("quantity")));
                settlReq.put("fillPrice", matchResult.getOrDefault("filledPrice", "0"));
            } catch (Exception e) {
                settlReq.put("quantity", orderEvent.get("quantity"));
                settlReq.put("fillPrice", orderEvent.getOrDefault("limitPrice", "0"));
            }
            return settlementClient.createSettlement(settlReq);
        });

        // Step 5: Complete saga
        saga.setStatus("COMPLETED");
        saga.setCurrentStep(5);
        saga.setCompletedAt(Instant.now());
        saga = sagaRepository.save(saga);
        log.info("OrderSaga COMPLETED for orderId={}", saga.getOrderId());
        return saga;
    }

    private SagaStep executeStep(Saga saga, int stepNumber, String stepName,
                                  java.util.function.Supplier<Map<String, Object>> action) {
        SagaStep step = SagaStep.builder()
                .saga(saga)
                .stepNumber(stepNumber)
                .stepName(stepName)
                .status("PENDING")
                .build();

        saga.setCurrentStep(stepNumber);
        sagaRepository.save(saga);

        try {
            Map<String, Object> result = action.get();
            String payload = "";
            try {
                payload = objectMapper.writeValueAsString(result);
            } catch (Exception ignored) {}

            step.setStatus("COMPLETED");
            step.setResponsePayload(payload);
            step.setExecutedAt(Instant.now());
            saga.getSteps().add(step);
            log.info("Saga step {}/{} COMPLETED for orderId={}", stepNumber, stepName, saga.getOrderId());
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
            step.setExecutedAt(Instant.now());
            saga.getSteps().add(step);
            log.error("Saga step {}/{} FAILED for orderId={}: {}", stepNumber, stepName, saga.getOrderId(), e.getMessage());
        }

        return step;
    }

    private Saga failSaga(Saga saga, String reason) {
        saga.setStatus("FAILED");
        saga.setFailureReason(reason);
        saga.setCompletedAt(Instant.now());
        saga = sagaRepository.save(saga);
        log.error("OrderSaga FAILED for orderId={}: {}", saga.getOrderId(), reason);
        return saga;
    }
}
