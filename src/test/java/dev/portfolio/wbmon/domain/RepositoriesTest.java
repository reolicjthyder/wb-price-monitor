package dev.portfolio.wbmon.domain;

import dev.portfolio.wbmon.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoriesTest extends AbstractPostgresTest {

    private static final long CHAT_ID = 42L;
    private static final Instant T0 = Instant.parse("2026-07-30T10:00:00Z");

    @Autowired
    private SkuRepository skuRepository;

    @Autowired
    private PriceSnapshotRepository snapshotRepository;

    @Autowired
    private BotConfigRepository botConfigRepository;

    @Test
    void savesAndFindsSkuByChatId() {
        skuRepository.save(new Sku(CHAT_ID, Marketplace.WB, "12345", T0));
        skuRepository.save(new Sku(CHAT_ID, Marketplace.OZON, "67890", T0));
        skuRepository.save(new Sku(999L, Marketplace.WB, "11111", T0));

        List<Sku> found = skuRepository.findByChatId(CHAT_ID);

        assertThat(found).hasSize(2)
                .extracting(Sku::getSkuCode)
                .containsExactlyInAnyOrder("12345", "67890");
        assertThat(found).allSatisfy(sku -> assertThat(sku.getCreatedAt()).isEqualTo(T0));
    }

    @Test
    void findsSkuByNaturalKey() {
        skuRepository.save(new Sku(CHAT_ID, Marketplace.WB, "12345", T0));

        Optional<Sku> found =
                skuRepository.findByChatIdAndMarketplaceAndSkuCode(CHAT_ID, Marketplace.WB, "12345");

        assertThat(found).isPresent();
        assertThat(found.get().getMarketplace()).isEqualTo(Marketplace.WB);
        assertThat(skuRepository.findByChatIdAndMarketplaceAndSkuCode(CHAT_ID, Marketplace.OZON, "12345"))
                .isEmpty();
    }

    @Test
    void rejectsDuplicateSkuForSameChatAndMarketplace() {
        skuRepository.saveAndFlush(new Sku(CHAT_ID, Marketplace.WB, "12345", T0));

        assertThatThrownBy(() -> skuRepository.saveAndFlush(new Sku(CHAT_ID, Marketplace.WB, "12345", T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsNewestSnapshotFirst() {
        Sku sku = skuRepository.save(new Sku(CHAT_ID, Marketplace.WB, "12345", T0));
        snapshotRepository.save(new PriceSnapshot(sku.getId(), new BigDecimal("1000.00"), 10, T0));
        snapshotRepository.save(new PriceSnapshot(sku.getId(), new BigDecimal("1100.00"), 8, T0.plusSeconds(600)));
        snapshotRepository.save(new PriceSnapshot(sku.getId(), new BigDecimal("1200.00"), 3, T0.plusSeconds(1200)));

        Optional<PriceSnapshot> latest = snapshotRepository.findTop1BySkuIdOrderByCheckedAtDescIdDesc(sku.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getPrice()).isEqualByComparingTo("1200.00");
        assertThat(latest.get().getQuantity()).isEqualTo(3);
        assertThat(latest.get().getCheckedAt()).isEqualTo(T0.plusSeconds(1200));
    }

    @Test
    void botConfigUsesDesignDefaults() {
        botConfigRepository.saveAndFlush(new BotConfig(CHAT_ID));

        BotConfig config = botConfigRepository.findById(CHAT_ID).orElseThrow();

        assertThat(config.getPollIntervalMin()).isEqualTo(20);
        assertThat(config.getPriceThresholdPct()).isEqualByComparingTo("3.00");
        assertThat(config.getQtyLowThreshold()).isEqualTo(5);
    }
}
