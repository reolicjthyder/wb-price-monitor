package dev.portfolio.wbmon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_snapshot")
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected PriceSnapshot() {
        // для JPA
    }

    public PriceSnapshot(Long skuId, BigDecimal price, int quantity, Instant checkedAt) {
        this.skuId = skuId;
        this.price = price;
        this.quantity = quantity;
        this.checkedAt = checkedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSkuId() {
        return skuId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
