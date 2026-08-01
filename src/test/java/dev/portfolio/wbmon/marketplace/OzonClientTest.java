package dev.portfolio.wbmon.marketplace;

import dev.portfolio.wbmon.domain.Marketplace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OzonClientTest {

    private final OzonClient client = new OzonClient();

    @Test
    void reportsItsMarketplace() {
        assertThat(client.marketplace()).isEqualTo(Marketplace.OZON);
    }

    @Test
    void returnsPlausibleRandomisedDataWithinBounds() {
        for (int i = 0; i < 200; i++) {
            ProductSnapshot snapshot = client.fetch("ozon-1");

            assertThat(snapshot.skuCode()).isEqualTo("ozon-1");
            assertThat(snapshot.price())
                    .isGreaterThanOrEqualTo(new BigDecimal("1890.50"))
                    .isLessThanOrEqualTo(new BigDecimal("2089.50"));
            assertThat(snapshot.quantity()).isBetween(0, 16);
        }
    }
}
