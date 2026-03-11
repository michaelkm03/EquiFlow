package com.equiflow.market.repository;

import com.equiflow.market.model.TickerPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TickerPriceRepository extends JpaRepository<TickerPrice, String> {
    Optional<TickerPrice> findByTicker(String ticker);
}
