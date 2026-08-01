package dev.portfolio.wbmon.poll;

import dev.portfolio.wbmon.alert.AlertEngine;
import dev.portfolio.wbmon.alert.AlertNotifier;
import dev.portfolio.wbmon.domain.BotConfig;
import dev.portfolio.wbmon.domain.BotConfigService;
import dev.portfolio.wbmon.domain.Marketplace;
import dev.portfolio.wbmon.domain.PriceSnapshot;
import dev.portfolio.wbmon.domain.PriceSnapshotRepository;
import dev.portfolio.wbmon.domain.Sku;
import dev.portfolio.wbmon.domain.SkuRepository;
import dev.portfolio.wbmon.marketplace.MarketplaceClient;
import dev.portfolio.wbmon.marketplace.MarketplaceFetchException;
import dev.portfolio.wbmon.marketplace.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PollerTest {

    private static final long OWNER = 42L;
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    private final SkuRepository skuRepository = mock(SkuRepository.class);
    private final PriceSnapshotRepository snapshotRepository = mock(PriceSnapshotRepository.class);
    private final BotConfigService botConfigService = mock(BotConfigService.class);
    private final AlertEngine alertEngine = mock(AlertEngine.class);
    private final AlertNotifier alertNotifier = mock(AlertNotifier.class);
    private final MarketplaceClient wbClient = mock(MarketplaceClient.class);
    private final MarketplaceClient ozonClient = mock(MarketplaceClient.class);

    private BotConfig config;

    @BeforeEach
    void setUp() {
        config = new BotConfig(OWNER);
        config.setPollIntervalMin(0); // 0 = опрашивать на каждом тике
        when(botConfigService.getOrCreate(OWNER)).thenReturn(config);
        when(wbClient.marketplace()).thenReturn(Marketplace.WB);
        when(ozonClient.marketplace()).thenReturn(Marketplace.OZON);
        when(snapshotRepository.findTop1BySkuIdOrderByCheckedAtDescIdDesc(anyLong()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any(PriceSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alertEngine.evaluate(any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    private Poller poller(Clock clock) {
        return new Poller(skuRepository, snapshotRepository, botConfigService, alertEngine, alertNotifier,
                List.of(wbClient, ozonClient), clock, OWNER);
    }

    private Poller poller() {
        return poller(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Sku sku(long id, Marketplace marketplace, String code) {
        return new Sku(id, OWNER, marketplace, code, NOW);
    }

    @Test
    void savesSnapshotForEachActiveSku() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(
                sku(1L, Marketplace.WB, "111"),
                sku(2L, Marketplace.OZON, "222")));
        when(wbClient.fetch("111")).thenReturn(new ProductSnapshot("111", new BigDecimal("100.00"), 5));
        when(ozonClient.fetch("222")).thenReturn(new ProductSnapshot("222", new BigDecimal("200.00"), 7));

        poller().tick();

        ArgumentCaptor<PriceSnapshot> captor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(snapshotRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PriceSnapshot::getSkuId, PriceSnapshot::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 5),
                        org.assertj.core.groups.Tuple.tuple(2L, 7));
        assertThat(captor.getAllValues()).allSatisfy(s -> assertThat(s.getCheckedAt()).isEqualTo(NOW));
    }

    @Test
    void routesEachSkuToTheClientOfItsMarketplace() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(sku(1L, Marketplace.OZON, "222")));
        when(ozonClient.fetch("222")).thenReturn(new ProductSnapshot("222", new BigDecimal("200.00"), 7));

        poller().tick();

        verify(ozonClient).fetch("222");
        verify(wbClient, never()).fetch(anyString());
    }

    @Test
    void oneFailingSkuDoesNotStopTheOthers() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(
                sku(1L, Marketplace.WB, "111"),
                sku(2L, Marketplace.WB, "222")));
        when(wbClient.fetch("111")).thenThrow(new MarketplaceFetchException("boom"));
        when(wbClient.fetch("222")).thenReturn(new ProductSnapshot("222", new BigDecimal("200.00"), 7));

        poller().tick();

        verify(snapshotRepository, times(1)).save(any(PriceSnapshot.class));
        verify(wbClient).fetch("222");
    }

    @Test
    void sendsAlertProducedByTheEngine() {
        Sku tracked = sku(1L, Marketplace.WB, "111");
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(tracked));
        when(wbClient.fetch("111")).thenReturn(new ProductSnapshot("111", new BigDecimal("100.00"), 5));
        when(alertEngine.evaluate(eq(tracked), any(), any(), eq(config))).thenReturn(Optional.of("цена упала"));

        poller().tick();

        verify(alertNotifier).send(OWNER, "цена упала");
    }

    @Test
    void sendsFailAlertExactlyOnceOnFifthConsecutiveFailure() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(sku(1L, Marketplace.WB, "111")));
        when(wbClient.fetch("111")).thenThrow(new MarketplaceFetchException("WB is down"));

        Poller poller = poller();
        for (int i = 0; i < 7; i++) {
            poller.tick();
        }

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(alertNotifier, times(1)).send(eq(OWNER), captor.capture());
        assertThat(captor.getValue()).contains("WB", "111", "5");
    }

    @Test
    void resetsFailureCounterAfterRecovery() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(sku(1L, Marketplace.WB, "111")));
        when(wbClient.fetch("111"))
                .thenThrow(new MarketplaceFetchException("down"))
                .thenThrow(new MarketplaceFetchException("down"))
                .thenThrow(new MarketplaceFetchException("down"))
                .thenThrow(new MarketplaceFetchException("down"))
                .thenReturn(new ProductSnapshot("111", new BigDecimal("100.00"), 5))
                .thenThrow(new MarketplaceFetchException("down"));

        Poller poller = poller();
        for (int i = 0; i < 6; i++) {
            poller.tick();
        }

        verifyNoInteractions(alertNotifier);
    }

    @Test
    void skipsPollWhenConfiguredIntervalHasNotElapsed() {
        config.setPollIntervalMin(20);
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of());

        Poller poller = poller();
        poller.tick();
        poller.tick();
        poller.tick();

        verify(skuRepository, times(1)).findByChatId(OWNER);
    }
}
