package com.equiflow.compliance.repository;

import com.equiflow.compliance.model.WashSaleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface WashSaleHistoryRepository extends JpaRepository<WashSaleHistory, UUID> {

    List<WashSaleHistory> findByUserIdAndTicker(UUID userId, String ticker);

    @Query("SELECT w FROM WashSaleHistory w WHERE w.userId = :userId AND w.ticker = :ticker " +
           "AND w.saleDate >= :from AND w.saleDate <= :to")
    List<WashSaleHistory> findRecentSales(UUID userId, String ticker, LocalDate from, LocalDate to);
}
