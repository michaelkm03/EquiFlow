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
            // Compliance API error — nothing committed downstream; case 1 skips compensation
            log.error("OrderSaga step1 compliance API error orderId={} userId={} reason={}",
                    saga.getOrderId(), saga.getUserId(), step1.getErrorMessage());
            return failSaga(saga, "Compliance check failed: " + step1.getErrorMessage(), 1);
        }

        // Verify compliance approved
        String compResult = step1.getResponsePayload();
        if (compResult != null && compResult.contains("\"approved\":false")) {
            // Compliance business rejection — order not yet matched or debited; case 1 skips compensation
            return failSaga(saga, "Order rejected by compliance check", 1);
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
            // Matching failed — order is PENDING/OPEN, not yet debited; case 2 cancels order only
            return failSaga(saga, "Order matching failed: " + step2.getErrorMessage(), 2);
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
            // Debit failed — order is FILLED, hold still frozen; case 3 cancels order and releases hold
            return failSaga(saga, "Ledger debit failed: " + step3.getErrorMessage(), 3);
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

    /**
     * Re-runs compensation for a saga stuck in COMPENSATING (called by SagaRecoveryJob).
     * Idempotency guards in each helper skip steps already COMPLETED.
     */
    @Transactional
    public void rerunCompensation(Saga saga) {
        int failedStep = saga.getCurrentStep();
        log.info("saga_recovery start orderId={} userId={} failedStep={}", saga.getOrderId(), saga.getUserId(), failedStep);

        switch (failedStep) {
            case 1 -> {
                // Nothing committed downstream at step 1 — no compensation needed
                log.info("saga_recovery step=1 orderId={} userId={} — no compensation required", saga.getOrderId(), saga.getUserId());
            }
            case 2 -> compensateCancel(saga);
            case 3 -> {
                compensateCancel(saga);
                compensateRelease(saga);
            }
            default -> log.warn("saga_recovery unrecognised failedStep={} orderId={} userId={}", failedStep, saga.getOrderId(), saga.getUserId());
        }

        saga.setStatus("FAILED");
        saga.setFailureReason("Recovered by SagaRecoveryJob after stuck in COMPENSATING");
        saga.setCompletedAt(Instant.now());
        sagaRepository.save(saga);
        log.info("saga_recovery complete orderId={} userId={} status=FAILED", saga.getOrderId(), saga.getUserId());
    }

    private Saga failSaga(Saga saga, String reason, int failedStep) {
        // Write COMPENSATING before any Feign call — crash-safe checkpoint for SagaRecoveryJob
        saga.setStatus("COMPENSATING");
        saga = sagaRepository.save(saga);
        log.info("saga_compensation checkpoint orderId={} userId={} failedStep={} status=COMPENSATING",
                saga.getOrderId(), saga.getUserId(), failedStep);

        // Each case isolates its Feign calls — a cancel failure must not block release from running
        switch (failedStep) {
            case 1 -> {
                // Nothing committed downstream at step 1 — no compensation needed
                log.info("saga_compensation step=1 orderId={} userId={} — no compensation required",
                        saga.getOrderId(), saga.getUserId());
            }
            case 2 -> {
                // Order is PENDING/OPEN — cancel it; nothing debited yet
                compensateCancel(saga);
            }
            case 3 -> {
                // Order is FILLED and hold is frozen — cancel order then release hold
                compensateCancel(saga);
                compensateRelease(saga);
            }
            default -> log.warn("saga_compensation unrecognised failedStep={} orderId={} userId={}",
                    failedStep, saga.getOrderId(), saga.getUserId());
        }

        saga.setStatus("FAILED");
        saga.setFailureReason(reason);
        saga.setCompletedAt(Instant.now());
        saga = sagaRepository.save(saga);
        log.error("OrderSaga FAILED orderId={} userId={} reason={}", saga.getOrderId(), saga.getUserId(), reason);
        return saga;
    }

    private void compensateCancel(Saga saga) {
        // Idempotency guard: skip if a COMPLETED COMPENSATION_CANCEL step already exists (e.g. recovery job re-run)
        boolean alreadyDone = saga.getSteps().stream()
                .anyMatch(s -> "COMPENSATION_CANCEL".equals(s.getStepName()) && "COMPLETED".equals(s.getStatus()));
        if (alreadyDone) {
            log.info("saga_compensation step=CANCEL orderId={} userId={} already COMPLETED — skipping",
                    saga.getOrderId(), saga.getUserId());
            return;
        }

        SagaStep step = new SagaStep();
        step.setSaga(saga);
        step.setStepNumber(0); // 0 = compensation step, not part of the main 1-5 sequence
        step.setStepName("COMPENSATION_CANCEL");
        step.setExecutedAt(Instant.now());
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", saga.getUserId().toString());
            orderClient.systemCancelOrder(saga.getOrderId(), body);
            step.setStatus("COMPLETED");
            log.info("saga_compensation step=CANCEL orderId={} userId={} outcome=COMPLETED",
                    saga.getOrderId(), saga.getUserId());
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
            log.error("saga_compensation step=CANCEL orderId={} userId={} outcome=FAILED reason={}",
                    saga.getOrderId(), saga.getUserId(), e.getMessage());
        }
        saga.getSteps().add(step);
    }

    private void compensateRelease(Saga saga) {
        // Idempotency guard: skip if a COMPLETED COMPENSATION_RELEASE step already exists (e.g. recovery job re-run)
        boolean alreadyDone = saga.getSteps().stream()
                .anyMatch(s -> "COMPENSATION_RELEASE".equals(s.getStepName()) && "COMPLETED".equals(s.getStatus()));
        if (alreadyDone) {
            log.info("saga_compensation step=RELEASE orderId={} userId={} already COMPLETED — skipping",
                    saga.getOrderId(), saga.getUserId());
            return;
        }

        SagaStep step = new SagaStep();
        step.setSaga(saga);
        step.setStepNumber(0); // 0 = compensation step, not part of the main 1-5 sequence
        step.setStepName("COMPENSATION_RELEASE");
        step.setExecutedAt(Instant.now());
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", saga.getUserId().toString());
            body.put("orderId", saga.getOrderId().toString());
            ledgerClient.release(body);
            step.setStatus("COMPLETED");
            log.info("saga_compensation step=RELEASE orderId={} userId={} outcome=COMPLETED",
                    saga.getOrderId(), saga.getUserId());
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
            log.error("saga_compensation step=RELEASE orderId={} userId={} outcome=FAILED reason={}",
                    saga.getOrderId(), saga.getUserId(), e.getMessage());
        }
        saga.getSteps().add(step);
    }
}
