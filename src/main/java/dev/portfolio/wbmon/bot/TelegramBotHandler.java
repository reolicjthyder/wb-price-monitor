package dev.portfolio.wbmon.bot;

import dev.portfolio.wbmon.alert.AlertNotifier;
import dev.portfolio.wbmon.domain.BotConfigService;
import dev.portfolio.wbmon.domain.Marketplace;
import dev.portfolio.wbmon.domain.PriceSnapshotRepository;
import dev.portfolio.wbmon.domain.Sku;
import dev.portfolio.wbmon.domain.SkuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Component
public class TelegramBotHandler extends TelegramLongPollingBot implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotHandler.class);
    private static final int SEND_ATTEMPTS = 2;
    private static final long SEND_RETRY_DELAY_MS = 1000L;
    private static final int MAX_SKU_CODE_LENGTH = 50;

    private static final String HELP = """
            Команды:
            /add [WB|OZON] <sku> — добавить артикул (по умолчанию WB)
            /remove [WB|OZON] <sku> — удалить артикул
            /list — список артикулов с последним снапшотом
            /interval <минуты> — интервал опроса, 1..1440
            /threshold <процент цены> <мин. остаток> — пороги алертов, например /threshold 5 3""";

    private final String botUsername;
    private final long ownerChatId;
    private final boolean configured;
    private final SkuRepository skuRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final BotConfigService botConfigService;
    private final Clock clock;

    public TelegramBotHandler(@Value("${wbmon.telegram.bot-token}") String botToken,
                              @Value("${wbmon.telegram.bot-username}") String botUsername,
                              @Value("${wbmon.owner-chat-id}") long ownerChatId,
                              SkuRepository skuRepository,
                              PriceSnapshotRepository snapshotRepository,
                              BotConfigService botConfigService,
                              Clock clock) {
        super(botToken);
        this.botUsername = botUsername;
        this.ownerChatId = ownerChatId;
        this.configured = !botToken.isBlank();
        this.skuRepository = skuRepository;
        this.snapshotRepository = snapshotRepository;
        this.botConfigService = botConfigService;
        this.clock = clock;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        if (chatId != ownerChatId) {
            log.warn("Ignoring command from unauthorized chat {}: {}", chatId, text);
            send(chatId, "⛔ Not authorized.");
            return;
        }

        try {
            send(chatId, handleCommand(text));
        } catch (RuntimeException e) {
            log.error("Command failed: {}", text, e);
            send(chatId, "Ошибка при выполнении команды: " + e.getMessage());
        }
    }

    /** Package-private, чтобы тесты дергали логику команд без сетевого транспорта. */
    String handleCommand(String text) {
        String[] parts = text.split("\\s+");
        return switch (parts[0].toLowerCase()) {
            case "/add" -> add(parts);
            case "/remove" -> remove(parts);
            case "/list" -> list();
            case "/interval" -> interval(parts);
            case "/threshold" -> threshold(parts);
            case "/start", "/help" -> HELP;
            default -> "Неизвестная команда.\n" + HELP;
        };
    }

    private String add(String[] parts) {
        SkuRef ref = parseRef(parts);
        if (ref == null) {
            return "Использование: /add [WB|OZON] <sku>";
        }
        if (ref.code().length() > MAX_SKU_CODE_LENGTH) {
            return "Слишком длинный артикул: максимум " + MAX_SKU_CODE_LENGTH + " символов.";
        }
        if (skuRepository.findByChatIdAndMarketplaceAndSkuCode(ownerChatId, ref.marketplace(), ref.code())
                .isPresent()) {
            return "Уже отслеживается: " + ref.marketplace() + " " + ref.code();
        }
        skuRepository.save(new Sku(ownerChatId, ref.marketplace(), ref.code(), clock.instant()));
        return "Добавлено: " + ref.marketplace() + " " + ref.code();
    }

    private String remove(String[] parts) {
        SkuRef ref = parseRef(parts);
        if (ref == null) {
            return "Использование: /remove [WB|OZON] <sku>";
        }
        return skuRepository.findByChatIdAndMarketplaceAndSkuCode(ownerChatId, ref.marketplace(), ref.code())
                .map(sku -> {
                    skuRepository.delete(sku);
                    return "Удалено: " + ref.marketplace() + " " + ref.code();
                })
                .orElse("Не найдено: " + ref.marketplace() + " " + ref.code());
    }

    private String list() {
        List<Sku> skus = skuRepository.findByChatId(ownerChatId);
        if (skus.isEmpty()) {
            return "Список пуст. Добавьте артикул: /add <sku>";
        }
        StringBuilder sb = new StringBuilder("Отслеживается артикулов: " + skus.size());
        for (Sku sku : skus) {
            String state = snapshotRepository.findTop1BySkuIdOrderByCheckedAtDescIdDesc(sku.getId())
                    .map(snapshot -> snapshot.getPrice().toPlainString() + " ₽, " + snapshot.getQuantity() + " шт.")
                    .orElse("данных пока нет");
            sb.append("\n• ").append(sku.getMarketplace()).append(' ').append(sku.getSkuCode())
                    .append(" — ").append(state);
        }
        return sb.toString();
    }

    private String interval(String[] parts) {
        if (parts.length != 2) {
            return "Использование: /interval <минуты>";
        }
        int minutes;
        try {
            minutes = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "Интервал должен быть целым числом минут.";
        }
        if (minutes < 1 || minutes > 1440) {
            return "Интервал должен быть от 1 до 1440 минут.";
        }
        botConfigService.updateInterval(ownerChatId, minutes);
        return "Интервал опроса: " + minutes + " мин.";
    }

    private String threshold(String[] parts) {
        if (parts.length != 3) {
            return "Использование: /threshold <процент цены> <мин. остаток>, например /threshold 5 3";
        }
        BigDecimal pricePct;
        int qtyLow;
        try {
            pricePct = new BigDecimal(parts[1]);
            qtyLow = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Пороги должны быть числами: /threshold 5 3";
        }
        if (pricePct.compareTo(new BigDecimal("0.1")) < 0 || pricePct.compareTo(new BigDecimal("100")) > 0) {
            return "Процент цены должен быть от 0.1 до 100.";
        }
        if (qtyLow < 0) {
            return "Порог остатка не может быть отрицательным.";
        }
        botConfigService.updateThresholds(ownerChatId, pricePct, qtyLow);
        return "Пороги обновлены: цена ±%s%%, мало осталось < %d шт.".formatted(pricePct.toPlainString(), qtyLow);
    }

    private record SkuRef(Marketplace marketplace, String code) {
    }

    private static SkuRef parseRef(String[] parts) {
        if (parts.length == 2) {
            return new SkuRef(Marketplace.WB, parts[1]);
        }
        if (parts.length == 3) {
            try {
                return new SkuRef(Marketplace.valueOf(parts[1].toUpperCase()), parts[2]);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void send(long chatId, String text) {
        if (!configured) {
            log.warn("Telegram is not configured (empty bot token); message dropped: {}", text);
            return;
        }
        SendMessage message = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text(text)
                .build();
        for (int attempt = 1; attempt <= SEND_ATTEMPTS; attempt++) {
            try {
                execute(message);
                return;
            } catch (TelegramApiException e) {
                log.error("Telegram send attempt {}/{} failed for chat {}", attempt, SEND_ATTEMPTS, chatId, e);
                if (attempt < SEND_ATTEMPTS) {
                    try {
                        Thread.sleep(SEND_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        // сообщение потеряно, но цикл поллинга не блокируем — так требует дизайн
    }
}
