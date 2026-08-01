package dev.portfolio.wbmon.bot;

import dev.portfolio.wbmon.domain.BotConfigService;
import dev.portfolio.wbmon.domain.Marketplace;
import dev.portfolio.wbmon.domain.PriceSnapshot;
import dev.portfolio.wbmon.domain.PriceSnapshotRepository;
import dev.portfolio.wbmon.domain.Sku;
import dev.portfolio.wbmon.domain.SkuRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramBotHandlerTest {

    private static final long OWNER = 42L;
    private static final long STRANGER = 999L;
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    private final SkuRepository skuRepository = mock(SkuRepository.class);
    private final PriceSnapshotRepository snapshotRepository = mock(PriceSnapshotRepository.class);
    private final BotConfigService botConfigService = mock(BotConfigService.class);

    // пустой токен: send() не пойдёт в сеть, а залогирует и выйдет
    private final TelegramBotHandler handler = new TelegramBotHandler(
            "", "test_bot", OWNER, skuRepository, snapshotRepository, botConfigService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Update textMessage(long chatId, String text) {
        Chat chat = new Chat();
        chat.setId(chatId);
        Message message = new Message();
        message.setChat(chat);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @Test
    void ignoresCommandsFromForeignChats() {
        handler.onUpdateReceived(textMessage(STRANGER, "/add 12345"));

        verifyNoInteractions(skuRepository);
        verifyNoInteractions(botConfigService);
    }

    @Test
    void executesCommandsFromTheOwner() {
        when(skuRepository.findByChatIdAndMarketplaceAndSkuCode(OWNER, Marketplace.WB, "12345"))
                .thenReturn(Optional.empty());

        handler.onUpdateReceived(textMessage(OWNER, "/add 12345"));

        verify(skuRepository).save(any(Sku.class));
    }

    @Test
    void addDefaultsToWildberries() {
        when(skuRepository.findByChatIdAndMarketplaceAndSkuCode(OWNER, Marketplace.WB, "12345"))
                .thenReturn(Optional.empty());

        String reply = handler.handleCommand("/add 12345");

        ArgumentCaptor<Sku> captor = ArgumentCaptor.forClass(Sku.class);
        verify(skuRepository).save(captor.capture());
        assertThat(captor.getValue().getMarketplace()).isEqualTo(Marketplace.WB);
        assertThat(captor.getValue().getChatId()).isEqualTo(OWNER);
        assertThat(captor.getValue().getSkuCode()).isEqualTo("12345");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(reply).contains("Добавлено", "WB", "12345");
    }

    @Test
    void addAcceptsExplicitMarketplace() {
        when(skuRepository.findByChatIdAndMarketplaceAndSkuCode(OWNER, Marketplace.OZON, "ozon-1"))
                .thenReturn(Optional.empty());

        String reply = handler.handleCommand("/add OZON ozon-1");

        ArgumentCaptor<Sku> captor = ArgumentCaptor.forClass(Sku.class);
        verify(skuRepository).save(captor.capture());
        assertThat(captor.getValue().getMarketplace()).isEqualTo(Marketplace.OZON);
        assertThat(reply).contains("Добавлено", "OZON");
    }

    @Test
    void addRejectsDuplicates() {
        when(skuRepository.findByChatIdAndMarketplaceAndSkuCode(OWNER, Marketplace.WB, "12345"))
                .thenReturn(Optional.of(new Sku(1L, OWNER, Marketplace.WB, "12345", NOW)));

        String reply = handler.handleCommand("/add 12345");

        assertThat(reply).contains("Уже отслеживается");
        verify(skuRepository, org.mockito.Mockito.never()).save(any(Sku.class));
    }

    @Test
    void addRejectsUnknownMarketplaceAndOverlongCode() {
        assertThat(handler.handleCommand("/add AMAZON 123")).contains("Использование: /add");
        assertThat(handler.handleCommand("/add " + "9".repeat(51))).contains("50");
        assertThat(handler.handleCommand("/add")).contains("Использование: /add");
    }

    @Test
    void removeDeletesTrackedSku() {
        Sku tracked = new Sku(1L, OWNER, Marketplace.WB, "12345", NOW);
        when(skuRepository.findByChatIdAndMarketplaceAndSkuCode(OWNER, Marketplace.WB, "12345"))
                .thenReturn(Optional.of(tracked));

        String reply = handler.handleCommand("/remove 12345");

        verify(skuRepository).delete(tracked);
        assertThat(reply).contains("Удалено", "12345");
    }

    @Test
    void removeReportsMissingSku() {
        when(skuRepository.findByChatIdAndMarketplaceAndSkuCode(OWNER, Marketplace.WB, "12345"))
                .thenReturn(Optional.empty());

        assertThat(handler.handleCommand("/remove 12345")).contains("Не найдено");
    }

    @Test
    void listReportsEmptyState() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of());

        assertThat(handler.handleCommand("/list")).contains("Список пуст");
    }

    @Test
    void listShowsLatestSnapshotPerSku() {
        when(skuRepository.findByChatId(OWNER)).thenReturn(List.of(
                new Sku(1L, OWNER, Marketplace.WB, "12345", NOW),
                new Sku(2L, OWNER, Marketplace.OZON, "ozon-1", NOW)));
        when(snapshotRepository.findTop1BySkuIdOrderByCheckedAtDescIdDesc(1L))
                .thenReturn(Optional.of(new PriceSnapshot(1L, new BigDecimal("1985.00"), 7, NOW)));
        when(snapshotRepository.findTop1BySkuIdOrderByCheckedAtDescIdDesc(2L))
                .thenReturn(Optional.empty());

        String reply = handler.handleCommand("/list");

        assertThat(reply).contains("WB 12345", "1985.00", "7", "OZON ozon-1", "данных пока нет");
    }

    @Test
    void intervalUpdatesConfiguration() {
        String reply = handler.handleCommand("/interval 30");

        verify(botConfigService).updateInterval(OWNER, 30);
        assertThat(reply).contains("30");
    }

    @Test
    void intervalRejectsOutOfRangeAndNonNumericValues() {
        assertThat(handler.handleCommand("/interval 0")).contains("от 1 до 1440");
        assertThat(handler.handleCommand("/interval 1441")).contains("от 1 до 1440");
        assertThat(handler.handleCommand("/interval abc")).contains("числом");
        assertThat(handler.handleCommand("/interval")).contains("Использование: /interval");
        verify(botConfigService, org.mockito.Mockito.never()).updateInterval(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void thresholdUpdatesBothThresholds() {
        String reply = handler.handleCommand("/threshold 5 3");

        verify(botConfigService).updateThresholds(OWNER, new BigDecimal("5"), 3);
        assertThat(reply).contains("5", "3");
    }

    @Test
    void thresholdRejectsOutOfRangeAndNonNumericValues() {
        assertThat(handler.handleCommand("/threshold 0 3")).contains("от 0.1 до 100");
        assertThat(handler.handleCommand("/threshold 101 3")).contains("от 0.1 до 100");
        assertThat(handler.handleCommand("/threshold 5 -1")).contains("не может быть отрицательным");
        assertThat(handler.handleCommand("/threshold abc 3")).contains("числами");
        assertThat(handler.handleCommand("/threshold 5")).contains("Использование: /threshold");
        verify(botConfigService, org.mockito.Mockito.never())
                .updateThresholds(anyLong(), any(BigDecimal.class), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unknownCommandReturnsHelp() {
        String reply = handler.handleCommand("/whatever");

        assertThat(reply).contains("Неизвестная команда", "/add", "/remove", "/list", "/interval", "/threshold");
    }

    @Test
    void startReturnsHelp() {
        assertThat(handler.handleCommand("/start"))
                .contains("/add", "/remove", "/list", "/interval", "/threshold");
    }
}
