package dev.portfolio.wbmon.marketplace;

import dev.portfolio.wbmon.domain.Marketplace;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Заглушка Ozon: возвращает слабо рандомизированные данные, чтобы alert-логике было что показывать.
 * TODO: real Ozon integration — подключение реального API = новая реализация MarketplaceClient,
 * Poller и AlertEngine при этом не меняются.
 */
@Component
public class OzonClient implements MarketplaceClient {

    private static final BigDecimal BASE_PRICE = new BigDecimal("1990.00");
    private static final int MAX_QUANTITY = 16;

    private final Random random = new Random();

    @Override
    public Marketplace marketplace() {
        return Marketplace.OZON;
    }

    @Override
    public ProductSnapshot fetch(String skuCode) {
        int deltaPct = random.nextInt(11) - 5; // -5%..+5%
        BigDecimal price = BASE_PRICE
                .multiply(BigDecimal.valueOf(100L + deltaPct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(MAX_QUANTITY + 1); // 0..16, ноль тоже возможен
        return new ProductSnapshot(skuCode, price, quantity);
    }
}
