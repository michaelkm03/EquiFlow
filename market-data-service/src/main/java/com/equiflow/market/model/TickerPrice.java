package com.equiflow.market.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ticker_prices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerPrice {

    @Id
    @Column(name = "ticker", length = 10)
    private String ticker;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "current_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "baseline_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal baselinePrice;

    @Column(name = "open_price", precision = 18, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
