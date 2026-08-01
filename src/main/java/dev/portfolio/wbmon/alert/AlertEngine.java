package dev.portfolio.wbmon.alert;

import dev.portfolio.wbmon.domain.BotConfig;
import dev.portfolio.wbmon.domain.PriceSnapshot;
import dev.portfolio.wbmon.domain.Sku;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Чистая логика: сравнивает предыдущий и новый снапшот и решает, слать ли алерт.
 * Ни БД, ни сети — поэтому покрывается обычными unit-тестами.
 */
@Component
public class AlertEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Optional<String> evaluate(Sku sku, PriceSnapshot previous, PriceSnapshot current, BotConfig config) {
        if (previous == null) {
            return Optional.empty(); // первый снапшот — сравнивать не с чем
        }

        List<String> lines = new ArrayList<>();
        priceLine(previous, current, config).ifPresent(lines::add);
        quantityLine(previous, current, config).ifPresent(lines::add);

        if (lines.isEmpty()) {
            return Optional.empty();
        }

        String header = "⚠️ " + sku.getMarketplace() + " " + sku.getSkuCode();
        return Optional.of(header + "\n" + String.join("\n", lines));
    }

    private Optional<String> priceLine(PriceSnapshot previous, PriceSnapshot current, BotConfig config) {
        BigDecimal oldPrice = previous.getPrice();
        BigDecimal newPrice = current.getPrice();
        if (oldPrice.signum() == 0 || oldPrice.compareTo(newPrice) == 0) {
            return Optional.empty();
        }

        BigDecimal deltaPct = newPrice.subtract(oldPrice)
                .multiply(HUNDRED)
                .divide(oldPrice, 2, RoundingMode.HALF_UP);
        if (deltaPct.abs().compareTo(config.getPriceThresholdPct()) < 0) {
            return Optional.empty();
        }

        String direction = deltaPct.signum() > 0 ? "выросла" : "упала";
        return Optional.of("Цена %s: %s → %s (%s%%)".formatted(
                direction, oldPrice.toPlainString(), newPrice.toPlainString(), deltaPct.toPlainString()));
    }

    private Optional<String> quantityLine(PriceSnapshot previous, PriceSnapshot current, BotConfig config) {
        int was = previous.getQuantity();
        int now = current.getQuantity();

        if (was > 0 && now == 0) {
            return Optional.of("Товар закончился (было " + was + " шт.)");
        }
        if (was == 0 && now > 0) {
            return Optional.of("Товар снова в наличии: " + now + " шт.");
        }

        int low = config.getQtyLowThreshold();
        if (now > 0 && now < low && was >= low) {
            return Optional.of("Мало осталось: " + now + " шт. (порог " + low + ")");
        }
        return Optional.empty();
    }
}
