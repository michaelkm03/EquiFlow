package com.equiflow.order.repository;

import com.equiflow.order.model.OrderBookEntry;
import com.equiflow.order.model.enums.OrderSide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderBookEntryRepository extends JpaRepository<OrderBookEntry, UUID> {
    List<OrderBookEntry> findByTickerAndSideOrderByPriceAsc(String ticker, OrderSide side);
    List<OrderBookEntry> findByTickerAndSideOrderByPriceDesc(String ticker, OrderSide side);
    void deleteByOrderId(UUID orderId);
    List<OrderBookEntry> findByTicker(String ticker);
}
