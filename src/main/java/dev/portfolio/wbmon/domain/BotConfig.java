package dev.portfolio.wbmon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "bot_config")
public class BotConfig {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "poll_interval_min", nullable = false)
    private int pollIntervalMin = 20;

    @Column(name = "price_threshold_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal priceThresholdPct = new BigDecimal("3.00");

    @Column(name = "qty_low_threshold", nullable = false)
    private int qtyLowThreshold = 5;

    protected BotConfig() {
        // для JPA
    }

    public BotConfig(Long chatId) {
        this.chatId = chatId;
    }

    public Long getChatId() {
        return chatId;
    }

    public int getPollIntervalMin() {
        return pollIntervalMin;
    }

    public void setPollIntervalMin(int pollIntervalMin) {
        this.pollIntervalMin = pollIntervalMin;
    }

    public BigDecimal getPriceThresholdPct() {
        return priceThresholdPct;
    }

    public void setPriceThresholdPct(BigDecimal priceThresholdPct) {
        this.priceThresholdPct = priceThresholdPct;
    }

    public int getQtyLowThreshold() {
        return qtyLowThreshold;
    }

    public void setQtyLowThreshold(int qtyLowThreshold) {
        this.qtyLowThreshold = qtyLowThreshold;
    }
}
