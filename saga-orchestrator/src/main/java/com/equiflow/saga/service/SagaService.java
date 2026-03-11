package com.equiflow.saga.service;

import com.equiflow.saga.model.Saga;
import com.equiflow.saga.orchestration.OrderSaga;
import com.equiflow.saga.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaService {

    private final SagaRepository sagaRepository;
    private final OrderSaga orderSaga;

    @Transactional
    public Saga startSaga(UUID orderId, UUID userId) {
        // Check if saga already exists for this order
        return sagaRepository.findByOrderId(orderId).orElseGet(() -> {
            Saga saga = Saga.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .status("STARTED")
                    .currentStep(0)
                    .build();
            return sagaRepository.save(saga);
        });
    }

    @Async
    public void executeAsync(Saga saga, Map<String, Object> orderEvent) {
        try {
            orderSaga.execute(saga, orderEvent);
        } catch (Exception e) {
            log.error("Saga execution error for orderId={}: {}", saga.getOrderId(), e.getMessage(), e);
        }
    }

    public List<Saga> getActiveSagas() {
        return sagaRepository.findByStatus("STARTED");
    }

    public List<Saga> getSagasByStatus(String status) {
        return sagaRepository.findByStatus(status);
    }

    public Saga getSaga(UUID sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
    }

    public Saga getSagaByOrderId(UUID orderId) {
        return sagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No saga found for order: " + orderId));
    }
}
