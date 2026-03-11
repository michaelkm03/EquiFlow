package com.equiflow.saga.repository;

import com.equiflow.saga.model.Saga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaRepository extends JpaRepository<Saga, UUID> {
    Optional<Saga> findByOrderId(UUID orderId);
    List<Saga> findByStatus(String status);
    List<Saga> findByUserId(UUID userId);
}
