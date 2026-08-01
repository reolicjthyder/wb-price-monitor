package dev.portfolio.wbmon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "sku")
public class Sku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 10)
    private Marketplace marketplace;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Sku() {
        // для JPA
    }

    public Sku(Long chatId, Marketplace marketplace, String skuCode, Instant createdAt) {
        this(null, chatId, marketplace, skuCode, createdAt);
    }

    /** id задаёт БД; конструктор с id нужен для тестов и для восстановления уже сохранённой сущности. */
    public Sku(Long id, Long chatId, Marketplace marketplace, String skuCode, Instant createdAt) {
        this.id = id;
        this.chatId = chatId;
        this.marketplace = marketplace;
        this.skuCode = skuCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getChatId() {
        return chatId;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
