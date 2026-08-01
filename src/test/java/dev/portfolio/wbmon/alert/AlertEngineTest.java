package dev.portfolio.wbmon.alert;

import dev.portfolio.wbmon.domain.BotConfig;
import dev.portfolio.wbmon.domain.Marketplace;
import dev.portfolio.wbmon.domain.PriceSnapshot;
import dev.portfolio.wbmon.domain.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEngineTest {

    private static final Instant T0 = Instant.parse("2026-07-30T10:00:00Z");
    private static final Sku SKU = new Sku(1L, 42L, Marketplace.WB, "12345", T0);

    private final AlertEngine engine = new AlertEngine();
    private BotConfig config;

    @BeforeEach
    void setUp() {
        config = new BotConfig(42L);
        config.setPriceThresholdPct(new BigDecimal("3.00"));
        config.setQtyLowThreshold(5);
    }

    private static PriceSnapshot snapshot(String price, int quantity) {
        return new PriceSnapshot(1L, new BigDecimal(price), quantity, T0);
    }

    @Test
    void staysSilentOnFirstSnapshot() {
        assertThat(engine.evaluate(SKU, null, snapshot("1000.00", 10), config)).isEmpty();
    }

    @Test
    void staysSilentWhenNothingChanged() {
        assertThat(engine.evaluate(SKU, snapshot("1000.00", 10), snapshot("1000.00", 10), config)).isEmpty();
    }

    @Test
    void alertsWhenPriceDropsBeyondThreshold() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 10), snapshot("900.00", 10), config);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("WB 12345", "упала", "1000.00", "900.00", "-10.00%");
    }

    @Test
    void alertsWhenPriceRisesBeyondThreshold() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 10), snapshot("1200.00", 10), config);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("выросла", "20.00%");
    }

    @Test
    void staysSilentWhenPriceChangeIsBelowThreshold() {
        assertThat(engine.evaluate(SKU, snapshot("1000.00", 10), snapshot("990.00", 10), config)).isEmpty();
    }

    @Test
    void alertsWhenPriceChangeIsExactlyAtThreshold() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 10), snapshot("970.00", 10), config);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("-3.00%");
    }

    @Test
    void doesNotDivideByZeroWhenPreviousPriceIsZero() {
        assertThat(engine.evaluate(SKU, snapshot("0.00", 10), snapshot("500.00", 10), config)).isEmpty();
    }

    @Test
    void alertsWhenItemGoesOutOfStock() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 4), snapshot("1000.00", 0), config);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("закончился");
    }

    @Test
    void alertsWhenItemIsBackInStock() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 0), snapshot("1000.00", 3), config);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("снова в наличии", "3");
    }

    @Test
    void alertsOnceWhenQuantityCrossesLowThreshold() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 6), snapshot("1000.00", 4), config);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("Мало осталось", "4");
    }

    @Test
    void staysSilentWhenQuantityWasAlreadyBelowThreshold() {
        assertThat(engine.evaluate(SKU, snapshot("1000.00", 4), snapshot("1000.00", 3), config)).isEmpty();
    }

    @Test
    void combinesPriceAndQuantityIntoOneMessage() {
        Optional<String> alert = engine.evaluate(SKU, snapshot("1000.00", 6), snapshot("800.00", 0), config);

        assertThat(alert).isPresent();
        assertThat(alert.get().lines()).hasSize(3);
        assertThat(alert.get()).contains("WB 12345", "-20.00%", "закончился");
    }

    @Test
    void respectsCustomThresholds() {
        config.setPriceThresholdPct(new BigDecimal("15.00"));
        config.setQtyLowThreshold(20);

        assertThat(engine.evaluate(SKU, snapshot("1000.00", 30), snapshot("900.00", 30), config)).isEmpty();
        assertThat(engine.evaluate(SKU, snapshot("1000.00", 30), snapshot("1000.00", 19), config)).isPresent();
    }
}
