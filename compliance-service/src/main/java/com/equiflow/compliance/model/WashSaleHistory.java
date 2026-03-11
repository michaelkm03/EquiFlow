package com.equiflow.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wash_sale_history", indexes = {
    @Index(name = "idx_wash_sale_user_ticker", columnList = "user_id, ticker"),
    @Index(name = "idx_wash_sale_date", columnList = "sale_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WashSaleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "sale_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "cost_basis", precision = 18, scale = 4)
    private BigDecimal costBasis;

    @Column(name = "loss_amount", precision = 18, scale = 4)
    private BigDecimal lossAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
