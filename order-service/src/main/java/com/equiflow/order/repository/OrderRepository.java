package com.equiflow.order.repository;

import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    List<Order> findByUserIdAndStatus(UUID userId, OrderStatus status);

    List<Order> findByStatus(OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.ticker = :ticker AND o.status IN ('OPEN', 'PARTIALLY_FILLED')")
    List<Order> findActiveOrdersByTicker(String ticker);

    List<Order> findBySagaId(UUID sagaId);
}
